package dong.yoogo.approval.flow.service;

import java.math.BigDecimal;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.validation.constraints.Min;

import dong.yoogo.approval.flow.constant.ConditionIN;
import dong.yoogo.approval.flow.constant.Constants;
import dong.yoogo.approval.flow.constant.ErrorCode;
import dong.yoogo.approval.flow.constant.FlowStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.BooleanExpression;

import dong.yoogo.approval.flow.domain.Flow;
import dong.yoogo.approval.flow.domain.FlowNode;
import dong.yoogo.approval.flow.domain.FlowRepository;
import dong.yoogo.approval.flow.domain.FunctionLimitWhiteRepository;
import dong.yoogo.approval.flow.domain.UserModelFlowDTO;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Service
@Transactional
@Slf4j
public class FlowService {

	@Autowired
	private FlowRepository repository;

	@Autowired
	private ApprovalModelService modelService;

	@Autowired
	private TransactionFeign transactionFeign;

	@Autowired
	private LimitFeign limitFeign;

	@Autowired
	private AccountFeign accountFeign;
	
	@Autowired
	private UserFeign userFeign;

	@Autowired
	private RestTemplate restTemplate;

	@Autowired
	private ChannelCustomerFeign channelCustomerFeign;

	@Autowired
	private FunctionLimitWhiteRepository functionLimitRepository;

	/**
	 * 新建流程,由交易调用
	 */
	public Flow createFlow(CreateFlowIN createFlowIN) {
		log.info("交易发起新的流程，交易信息：{}", createFlowIN);
		// 根据交易传入的参数，创建一个空流程
		Flow flow = new Flow(createFlowIN);

		List<ApprovalNodeDTO> modelNodeList = null;

		modelNodeList = getNodeListFromApprovalModel(createFlowIN);

		if (modelNodeList.size() == 0) {
			log.info(" 审批级别为0级,参数:modelNodeList= {}", modelNodeList);
			log.info("开始做功能关闭、限额、余额检查");
			approvalCheck(flow);
			flow.zeroNode();
		} else {
			log.info("采用以下模式：{}", modelNodeList);
			flow.useSpecifiedFlow(modelNodeList);
		}
		log.debug("保存flow:{}", flow);
		return repository.save(flow);
	}

	public void approvalCheck(Flow flow) {
		checkFunctionClose(flow);
		checkLimit(flow);
		checkBalance(flow);
	}
	/**
	 * 从复核模式中查询是否有配置好的复核流程
	 */
	private List<ApprovalNodeDTO> getNodeListFromApprovalModel(CreateFlowIN createFlowIN) {
		Long customerNo = createFlowIN.getCustomerNo();
		String accountNo = createFlowIN.getAccountNo();
		// 当校验账户不为空，则说明accountNo是子账号，此时需要用主账户去获取复核模型
		String mainAccountNo = createFlowIN.getMainAccountNo();
		if (StringUtils.isNotEmpty(mainAccountNo)) {
			accountNo = mainAccountNo;
		}
		String functionCode = createFlowIN.getFunctionCode();
		String creatorNo = createFlowIN.getCreatorNo();
		if (!StringUtils.isNumeric(creatorNo)) {
			// 制单员不对，则赋值为默认值-1L
			creatorNo = "-1";
		}
		return modelService.findApprovalNodes(customerNo, accountNo, functionCode, Long.valueOf(creatorNo), createFlowIN.getAmount());
	}

	/**
	 * 查询复核待办,返回分页对象
	 *
	 * @param page
	 *            zero-based page index.
	 * @param size
	 *            the size of the page to be returned.
	 */
	@Transactional(readOnly = true)
	public Page<Flow> findTodoPage(String approverId, int page, int size) {
		log.info("查询复核待办: approverId={},page={},size={}", approverId, page, size);
		Sort orders = Sort.by("createTime").descending();
		QFlow flow = QFlow.flow;
		BooleanExpression isApprover = flow.approverList.any().eq(approverId);
		BooleanExpression excludeCreator = flow.creatorNo.ne(approverId);
		BooleanExpression canApproval = flow.flowStatus.in(FlowStatus.WAIT_FOR_REVIEW, FlowStatus.IN_PROCESS);
		return repository.findAll(isApprover.and(excludeCreator).and(canApproval), PageRequest.of(page, size, orders));
	}

	/**
	 * 同意复核
	 * @param dayOutLimit 
	 *
	 * @return
	 */
	public Flow approve(String transactionNo, String approverId, BigDecimal dayLimitUsed) {
		log.info("单笔复核,transactionNO={},approverId={}", transactionNo, approverId);
		Flow flow = findFlow(transactionNo);
		//白名单同步限制
		if (flow.approve(approverId, dayLimitUsed,needCheckLimit(flow))) {
			clearAllNodes(flow);
		}
		return flow;
	}

	/**
	 * 批量同意
	 *
	 * @return 完成的 Flow 列表
	 */
	public List<Flow> approve(List<String> transactionNoList, String approverId) {
		log.info("批量复核同意: transactionNoList={},approverId={}", transactionNoList, approverId);
		List<Flow> completedTrasactions = new ArrayList<>();
		repository.findAllById(transactionNoList).forEach(flow -> {
			if (flow.approve(approverId, null,needCheckLimit(flow))) {
				clearAllNodes(flow);
				completedTrasactions.add(flow);
			}
		});
		return completedTrasactions;
	}

	/**
	 * 批量驳回 相同的驳回理由
	 *
	 * @return
	 */
	public List<Flow> reject(List<String> transactionNoList, String approverId, String rejectReason) {
		List<Flow> rejectedTransaction = new ArrayList<>();
		repository.findAllById(transactionNoList).forEach(flow -> {
			flow.reject(approverId, rejectReason);
			clearAllNodes(flow);
			rejectedTransaction.add(flow);
		});
		return rejectedTransaction;
	}

	/**
	 * 将复核流程的结果(完成/驳回)回复给交易 不同的交易类型的 url 不同, 所以 url 中有一个变量,代表要回调的是哪个交易 将来交易服务也可能是不同的
	 */
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public void notifyTransaction(String transactionNo, FlowStatus status) {
		final Flow flow = findFlow(transactionNo);

		// final String functionCode = "innertransfer"; // todo: 将来应该切换成功能码
		final TransactionRequestOUT out = new TransactionRequestOUT(transactionNo, status.toString());
		HttpEntity<TransactionRequestOUT> request = headerAndBody(out);
		new Thread(new Runnable() {

			@Override
			public void run() {
				try {
					log.info("通知交易服务,审批流程已经终结, 参数 ={}", out);
					restTemplate.postForLocation(flow.getCallBackUrl(), request);
					log.info("已通知交易,交易处理中");
				} catch (Exception e) {
					String message = "无法正确连接交易服务, 这意味着此交易虽然已完成复核流程/驳回, 但是交易最终没有被提交,需要人工干预";
					log.error(message, e);
					updateTransactionResult(transactionNo, FlowStatus.TRANSACTION_INPROCESS,
							ErrorCode.FAIL_TO_NOTIFY_TRANSACTION, message);
					throw BusinessUtils.createBusinessException(ErrorCode.HEAD + ErrorCode.FAIL_TO_NOTIFY_TRANSACTION);
				}
			}
		}).start();

	}

	private HttpEntity<TransactionRequestOUT> headerAndBody(TransactionRequestOUT out) {
		HttpHeaders headers = new HttpHeaders();
		ArrayList<MediaType> acceptableMediaTypes = new ArrayList<>();
		acceptableMediaTypes.add(MediaType.APPLICATION_JSON_UTF8);
		headers.setAccept(acceptableMediaTypes);
		headers.setContentType(MediaType.APPLICATION_JSON_UTF8);
		headers.set("ZY-Head", JSON.toJSONString(CurrentHeaderHolder.getCurrentZyHeader()));
		return new HttpEntity<>(out, headers);
	}

	/**
	 * 流程完成后,删除所有节点
	 */
	private void clearAllNodes(Flow flow) {
		log.info("流程完成,删除所有节点:{}", flow.getNodeList());
		flow.getNodeList().clear();
	}

	/**
	 * 使用流水号查流程
	 */
	@Transactional(readOnly = true)
	public Flow findFlow(String transactionNo) {
		log.debug("查询流程:transactionNo{}", transactionNo);
		return repository.findById(transactionNo).orElseThrow(
				() -> BusinessUtils.createBusinessException(ErrorCode.HEAD + ErrorCode.TRANSACTION_NOT_EXISTS));
	}

	/**
	 * 复核员查询自己响应过的流程
	 *
	 * @return
	 */
	@Transactional(readOnly = true)
	public Page<Flow> findHandledFlow(String approverId, ConditionIN condition, int page, int size) {
		log.info("查询已复核列表, 审核人:{},查询条件={},page={},size={}", approverId, condition, page, size);
		QFlow flow = QFlow.flow;
		BooleanExpression expression = flow.handledApproverList.any().userNo.eq(approverId);

		for (Predicate pre : condition2PredicateList(condition, flow)) {
			expression = expression.and(pre);
		}
		// 根据最后复核时间排序
		Sort sort = Sort.by("lastApprovalDate").descending();
		return repository.findAll(expression, PageRequest.of(page, size, sort));
	}

	private List<Predicate> condition2PredicateList(ConditionIN condition, QFlow flow) {
		List<Predicate> predicates = new ArrayList<>();
		if (condition == null)
			return predicates;

		String accountNo = condition.getAccountNo();
		FlowStatus flowStatus = condition.getFlowStatus();
		List<String> functionCodeList = condition.getFunctionCodeList();
		BigDecimal minAmount = condition.getMinAmount();
		BigDecimal maxAmount = condition.getMaxAmount();
		String channelOfCreated = condition.getChannelOfCreated();
		LocalDate approvalDateFrom = condition.getApprovalDateFrom();
		LocalDate approvalDateTo = condition.getApprovalDateTo();

		if (!StringUtil.isEmpty(accountNo)) {
			predicates.add(flow.accountNo.eq(accountNo));
		}
		if (flowStatus != null) {
			predicates.add(flow.flowStatus.eq(flowStatus));
		}
		if (functionCodeList != null) {
			functionCodeList = functionCodeList.stream().filter(x -> x.trim().length() != 0)
					.collect(Collectors.toList());
			if (functionCodeList.size() != 0)
				predicates.add(flow.functionCode.in(functionCodeList));
		}
		if (minAmount != null) {
			predicates.add(flow.transactionAmount.goe(minAmount));
		}
		if (maxAmount != null) {
			predicates.add(flow.transactionAmount.loe(maxAmount));
		}
		if (!StringUtil.isEmpty(channelOfCreated)) {
			predicates.add(flow.channelId.eq(channelOfCreated));
		}
		if (approvalDateFrom != null) {
			LocalDateTime approvalDateTimeFrom = approvalDateFrom.atTime(0, 0);
			predicates.add(flow.handledApproverList.any().handleTime.after(approvalDateTimeFrom));
		}

		if (approvalDateTo != null) {
			LocalDateTime approvalDateTimeTo = approvalDateTo.atTime(23, 59, 59);
			predicates.add(flow.handledApproverList.any().handleTime.before(approvalDateTimeTo));
		}
		return predicates;
	}

	/**
	 * 根据客户号和账号，删除还在活动的流程
	 */
	public void deleteActiveFlowWithCustomerNoAndAccountNo(Map<String,String> inMap) {
		if(null == inMap || null == inMap.get("customerNo")) {
			log.error("删除还在活动的流程,参数 'customerNo' 为空");
			throw BusinessUtils.createBusinessException(ErrorCode.HEAD + ErrorCode.PARAMETER_NOT_NULL, "客户号");
		}
		if(!inMap.get("customerNo").matches("[0-9]+")) {
			log.error("删除还在活动的流程,参数 'customerNo' 不为数字");
			throw BusinessUtils.createBusinessException(ErrorCode.HEAD + ErrorCode.PARAMETER_ERROR, "客户号不为数字");
		}
		if(null == inMap.get("accountNo")) {
			log.error("删除还在活动的流程,参数  'accountNo' 为空");
			throw BusinessUtils.createBusinessException(ErrorCode.HEAD + ErrorCode.PARAMETER_NOT_NULL,"账号");
		}
		
		List<FlowStatus> flowStatusList = new ArrayList<FlowStatus>();
		flowStatusList.add(FlowStatus.IN_PROCESS);
		flowStatusList.add(FlowStatus.WAIT_FOR_REVIEW);
		flowStatusList.add(FlowStatus.WAIT_FOR_LANDING_REVIEW);
		repository.deleteByCustomerNoAndAccountNoAndFlowStatusIn(Long.valueOf(inMap.get("customerNo")), inMap.get("accountNo"), flowStatusList);
		return;
	}

	/**
	 * 查询指定的账户下是否还有正在进行的流程, 后管使用
	 */
	@Transactional(readOnly = true)
	public boolean hasActiveFlow(String accountNo) {
		int inWait = repository.countByAccountNoAndFlowStatus(accountNo, FlowStatus.WAIT_FOR_REVIEW);
		int inProcess = repository.countByAccountNoAndFlowStatus(accountNo, FlowStatus.IN_PROCESS);
		int inWaitLanding = repository.countByAccountNoAndFlowStatus(accountNo, FlowStatus.WAIT_FOR_LANDING_REVIEW);
		return inWait + inProcess + inWaitLanding > 0;
	}

	/**
	 * 检查限额, 远程调用限额服务, 如果不足,限额服务会抛出异常 todo: 什么业务交易要检查
	 *
	 * @param
	 */
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public void checkLimit(Flow flow) {
		if (!needCheckLimit(flow)) {
			log.info("不需要检查限额, 制单号:{},功能:{},账户:{}", flow.getTransactionNo(), flow.getFunctionCode(),
					flow.getAccountNo());
			return;
		}
		String customerNo = flow.getCustomerNo().toString();
		String accountNo = flow.getParentAccountNo();
		if (StringUtil.isEmpty(accountNo))
			accountNo = flow.getAccountNo();
		String channelId = flow.getChannelId();
		String amount = flow.getTransactionAmount().toString();
		LimitCheckOUT limitCheckOUT = new LimitCheckOUT(customerNo, accountNo, channelId, amount);
		log.info("远程调用,检查限额, 出参:{}", limitCheckOUT);
		limitFeign.limitCheck(limitCheckOUT);
	}
	
	/**
	 * 检查功能关闭（包含渠道、渠道客户、渠道客户功能关闭）, 远程调用客户渠道服务, 如果关闭,需要抛出异常
	 *
	 * @param
	 */
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public void checkFunctionClose(Flow flow) {
		String functionCode = flow.getFunctionCode();
		long customerNo = flow.getCustomerNo();
		String channelId = flow.getChannelId();
		CustomerChannelCloseDTO customerChannelCloseDTO = new CustomerChannelCloseDTO(customerNo, channelId);
		customerChannelCloseDTO.getFunctions().add(functionCode);
	    boolean functionClose = userFeign.checkFunctionClose(customerChannelCloseDTO);
	    if(functionClose) {
			throw BusinessUtils.createBusinessException(ErrorCode.HEAD + ErrorCode.FUNCTION_CLOSE);
	    }
	}
	
	/**
	 * 获取账户当日累计限额, 远程调用限额服务, 如果获取失败,限额服务会抛出异常
	 *
	 * @param
	 */
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public BigDecimal queryDayLimitUsed(Flow flow) {
		//白名单同步限制
		if(!needCheckLimit(flow)) {
			log.info("不需要检查限额,日累计,落地复核, 制单号:{},功能:{},账户:{}", flow.getTransactionNo(), flow.getFunctionCode(),
					flow.getAccountNo());
			return null;
		}
		
		if (!needCheckDayOutLimit(flow)) {
			log.info("不需要检查日累计对外支出限额, 制单号:{},功能:{},账户:{}", flow.getTransactionNo(), flow.getFunctionCode(),
					flow.getAccountNo());
			return null;
		}
		String customerNo = flow.getCustomerNo().toString();
		String accountNo = flow.getParentAccountNo();
		if (StringUtil.isEmpty(accountNo)) {
			accountNo = flow.getAccountNo();
		}
		String channelId = flow.getChannelId();
		LimitCheckOUT limitCheckOUT = new LimitCheckOUT(customerNo, accountNo, channelId);
		log.info("远程调用,获取日累计限额, 入参:{}", limitCheckOUT);
		AccountLimit accountLimit = limitFeign.getAccountLimit(limitCheckOUT);
		return accountLimit.getDayLimitUsed();
	}

	private boolean needCheckDayOutLimit(Flow flow) {
		// 流程节点总数小于需要操作的节点，提示'该笔交易已提交，请勿重复操作'.(需要操作的节点下标从0开始，所以需要加1)
		if(null == flow.getNodeList() || (flow.getNodeList().size() < flow.getCurrentLevelIndex() + 1)) {
			throw BusinessUtils.createBusinessException(ErrorCode.HEAD + ErrorCode.ALREADY_SUBMIT);
		}
		
		FlowNode flowNode = flow.getNodeList().get(flow.getCurrentLevelIndex());
		BigDecimal dayOutAmount = flowNode.getDayOutAmount();
		if(null != dayOutAmount && dayOutAmount.compareTo(new BigDecimal(0)) > 0) {
			return true;
		}
		return false;
	}

	/**
	 * 需要限额检查的前提条件
	 */
	public boolean needCheckLimit(Flow flow) {
		//看白名单表是否有该功能，如果存在，则不需要做限额检查
		if (!flow.isAccountOf())
			return false;
		boolean existsById = functionLimitRepository.existsById(flow.getFunctionCode());
		if (existsById) {
			log.info("本业务不需要限额检查,制单号:{}", flow.getTransactionNo());
			return false;
		}
		log.info("本业务需要限额检查,制单号:{}", flow.getTransactionNo());
		return true;
	}

	/**
	 * 检查余额 远程调用核心系统,查询账户信息, 如果不足, 则自己抛出业务异常
	 */
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public void checkBalance(Flow flow) {
		BigDecimal balance = getBalanceIfNeedCheck(flow);
		if (balance == null) {
			log.info("不需要检查余额, 制单号:{},功能:{},账户:{}", flow.getTransactionNo(), flow.getFunctionCode(),
					flow.getAccountNo());
			return;
		}

		if (balance.compareTo(flow.getTransactionAmount()) < 0) {
			log.warn("余额不足,抛出异常");
			throw BusinessUtils.createBusinessException(ErrorCode.HEAD + ErrorCode.BALANCE_INSUFFICIENT);
		}
	}

	/**
	 * 需要检查余额的前提条件, 因为需要向核心查询,所以直接查询了余额
	 *
	 * @return 账户余额
	 */
	private BigDecimal getBalanceIfNeedCheck(Flow flow) {
		if (!flow.isAccountOf())
			return null;
		log.info("调用核心服务,查询账户余额,账户:{},交易额度:{}", flow.getAccountNo(), flow.getTransactionAmount());
		// Feign调用ECHC服务，获取账户信息
		Map<String,Object> accountInfoMap = new HashMap<String,Object>();
		try {
			accountInfoMap.put("account", flow.getAccountNo());
			// 定期转活期
			if(Constants.FIX_TO_CUR_FUNCTION_CODE.equals(flow.getFunctionCode())) {
				JSONObject extraData = JSONObject.parseObject(flow.getExtraData());
				accountInfoMap.put("certificateNo", extraData.get("certificateNo")==null?"":extraData.get("certificateNo"));
			}
			accountInfoMap = channelCustomerFeign.accountInfoQuery(accountInfoMap);
		}catch(Exception e) {
			log.error("调用【账户信息查询】接口异常:" + e.getMessage(), e);
			//添加异常信息
			throw BusinessUtils.createBusinessException(SystemConfigConsts.SYSTEM_CODE + "." + ErrCodeConsts.ACCOUNT_QUERY_EXCEPTION );
		}
		AccountResponse coreInfo = new AccountResponse();
		coreInfo.setAcctName(accountInfoMap.get("acct_name")==null?"":String.valueOf(accountInfoMap.get("acct_name")));
		coreInfo.setAcctStatus(accountInfoMap.get("acct_status")==null?"":String.valueOf(accountInfoMap.get("acct_status")));
		coreInfo.setAvailableBal(accountInfoMap.get("available_bal")==null?"":String.valueOf(accountInfoMap.get("available_bal")));
		coreInfo.setBaseAcctNo(accountInfoMap.get("base_acct_no")==null?"":String.valueOf(accountInfoMap.get("base_acct_no")));
		coreInfo.setSubAcctFlag(accountInfoMap.get("sub_acct_flag")==null?"":String.valueOf(accountInfoMap.get("sub_acct_flag")));
		coreInfo.setCP_FLAG(accountInfoMap.get("cp_flag")==null?"N":String.valueOf(accountInfoMap.get("cp_flag")));
		coreInfo.setCHK_BAL_FLAG(accountInfoMap.get("chk_bal_flag")==null?"":String.valueOf(accountInfoMap.get("chk_bal_flag")));
		log.info("调用核心服务,查询账户余额,账户:{},返回:{}", flow.getAccountNo(), coreInfo);
		String cp_flag = coreInfo.getCP_FLAG();
		String chk_bal_flag = coreInfo.getCHK_BAL_FLAG();
		if ("Y".equals(chk_bal_flag) || "Y".equals(cp_flag))
			return null;
		return new BigDecimal(coreInfo.getAvailableBal());
	}

	@Transactional(readOnly = true)
	public Page<Flow> findLandingTodo(Long departmentNo, LocalDate createTimeFrom, LocalDate createTimeTo, int page,
			int size) {
		QFlow flow = QFlow.flow;
		BooleanExpression waitLandingReview = flow.flowStatus.eq(FlowStatus.WAIT_FOR_LANDING_REVIEW);
		BooleanExpression department = flow.landingAppoval.departmentNo.eq(departmentNo);
		BooleanExpression from = flow.createTime.after(createTimeFrom.atTime(0, 0, 0));
		BooleanExpression to = flow.createTime.before(createTimeTo.atTime(23, 59, 59));
		return repository.findAll(waitLandingReview.and(department).and(from).and(to), PageRequest.of(page, size));
	}

	public Flow landingApprove(String transactionNo, String tellerId, String tellerName) {
		Flow flow = findFlow(transactionNo);
		flow.landingApprove(tellerId, tellerName);
		return flow;
	}

	public Flow landingReject(String transactionNo, String tellerId, String tellerName, String rejectReason) {
		Flow flow = findFlow(transactionNo);
		flow.landingReject(tellerId, tellerName, rejectReason);
		return flow;
	}

	/**
	 * 将交易的结果更新到流程
	 */
	public void updateTransactionResult(String transactionNo, FlowStatus transactionResult, String code,
			String message) {
		this.findFlow(transactionNo).transactionResult(transactionResult, code, message);
	}

    /**
     * 制单查询
     */
    @Transactional(readOnly = true)
    public Page<Flow> findCreatedFlow(FindCreatedFlowIN in) {
        String creatorNo = in.getCreatorNo();
        String functionCode = in.getFunctionCode();
        String accountNo = in.getAccountNo();
        LocalDate createDateTo = in.getCreateDateTo();
        LocalDate createDateFrom = in.getCreateDateFrom();
        @Min(0) int page = in.getPage();
        @Min(1) int size = in.getSize();
        String status = in.getFlowStatus();
        BigDecimal minAmount = in.getMinAmount();
		BigDecimal maxAmount = in.getMaxAmount();

        QFlow flow = QFlow.flow;
        BooleanBuilder condition = new BooleanBuilder();
		if (!StringUtil.isEmpty(creatorNo)) {
			condition.and(flow.creatorNo.eq(creatorNo));
		}
		// 功能码可以传单个或多个，当为多个时用英文逗号分隔开，例： 201,202,205
		if (!StringUtil.isEmpty(functionCode)) {
			List<String> functionCodeList = Arrays.asList(functionCode.split(","));
			condition.andAnyOf(flow.functionCode.in(functionCodeList));
		}
		if (!StringUtil.isEmpty(accountNo)) {
			condition.and(flow.accountNo.eq(accountNo));
		}
		if (createDateFrom != null) {
			condition.and(flow.createTime.after(createDateFrom.atTime(0, 0, 0)));
		}
		if (createDateTo != null) {
			condition.and(flow.createTime.before(createDateTo.atTime(23, 23, 59)));
		}
		if (!StringUtil.isEmpty(status)) {
			condition.and(flow.flowStatus.eq(FlowStatus.valueOf(status)));
		}
		if (minAmount != null) {
			condition.and(flow.transactionAmount.goe(minAmount));
		}
		if (maxAmount != null) {
			condition.and(flow.transactionAmount.loe(maxAmount));
		}

        return repository.findAll(condition, PageRequest.of(page, size, Sort.by("createTime").descending()));
    }

    public AccountFeign.AccountIN getLandingApprovalInfo(Long customerNo, String accountNo) {
        log.info("远程调用账户服务,获取账户的落地复核额度和机构号,账户:{}", accountNo);
        AccountFeign.AccountIN accountIN = accountFeign.getInfoForLandingApproval(new AccountFeign.AccountOUT(customerNo,accountNo));
        log.info("账户服务返回的信息:{}", accountIN);
        return accountIN;
    }

    @Data
    public static class FindCreatedFlowIN {
        String creatorNo;
        String functionCode;
        String accountNo;
        @DateTimeFormat(pattern = "yyyy-MM-dd")
        LocalDate createDateFrom;
        @DateTimeFormat(pattern = "yyyy-MM-dd")
        LocalDate createDateTo;
        @Min(0)
        int page;
        @Min(1)
        int size;
        BigDecimal minAmount;
        BigDecimal maxAmount;
        String flowStatus;

    }
    
    /**
	 * 删除状态为业务已受理的制单流程,由交易调用
	 */
	public Optional<Flow> deleteOrderFlow(CreateFlowIN createFlowIN) {
		log.info("交易发起删除制单的流程，制单信息：{}", createFlowIN);
		String transactionNo = createFlowIN.getTransactionNo();
		// 查询制单信息
		Optional<Flow> flow = repository.findById(transactionNo);
		
		if(!flow.isPresent()) {
			log.info("该制单不存在，制单号:{}", createFlowIN.getTransactionNo());
			throw BusinessUtils.createBusinessException(ErrorCode.HEAD + ErrorCode.ORDER_NOT_EXISTS);
		}
		repository.delete(flow.get());
		//调用交易服务，删除交易流程制单记录
		OrderOUT out = new OrderOUT();
		out.setOrderNo(transactionNo);
		out.setUserNo(Long.parseLong(createFlowIN.getCreatorNo()));
		transactionFeign.delOrder(out);
		return flow;
	}
	
	public void deleteFlowByCustomer(Long customerNo, List<String> channelIds) {
		// 如果渠道为空，则是注销客户，则删除该客户所有的交易
		if (channelIds == null || channelIds.isEmpty()) {
			repository.deleteByCustomerNo(customerNo);
			return;
		}		
		// 如果渠道不为空，则删除客户所有该渠道的交易
		repository.deleteByCustomerNoAndChannelIdIn(customerNo, channelIds);
		return;
	}
	
	public void deleteFlowByCustomerAndUsers(List<UserModelFlowDTO> userNos, Long customerNo) {
		//如果操作员为空，则删除所有该客户的交易
		if(userNos.isEmpty()) {
			repository.deleteByCustomerNo(customerNo);
			return;
		}
		//操作员不为空，需要根据操作员的渠道来删除交易
		List<FlowStatus> flowStatusList = new ArrayList<FlowStatus>();
		flowStatusList.add(FlowStatus.IN_PROCESS);
		flowStatusList.add(FlowStatus.WAIT_FOR_REVIEW);
		List<Flow> flows = repository.findByCustomerNoAndFlowStatusIn(customerNo,flowStatusList);
		List<Flow> deleteFlowList = new ArrayList<Flow>();
		//对交易进行遍历
		for (Flow flow : flows) {
			//如果交易包含了该操作员集合中的操作员，则需要删除
			if(flow.conatainUserNos(userNos)) {
				deleteFlowList.add(flow);
			}
		}
		repository.deleteAll(deleteFlowList);
	}
}