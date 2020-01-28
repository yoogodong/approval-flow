package dong.yoogo.approval.flow.web.rest;

import dong.yoogo.approval.flow.constant.ConditionIN;
import dong.yoogo.approval.flow.constant.ErrorCode;
import dong.yoogo.approval.flow.constant.FlowStatus;
import dong.yoogo.approval.flow.domain.CancelCustomerModelFlowDTO;
import dong.yoogo.approval.flow.domain.CancelUserModelFlowDTO;
import dong.yoogo.approval.flow.domain.Flow;
import dong.yoogo.approval.flow.service.ApprovalModelService;
import dong.yoogo.approval.flow.service.CreateFlowIN;
import dong.yoogo.approval.flow.service.FlowService;
import com.fasterxml.jackson.annotation.JsonFormat;
import dong.yoogo.approval.flow.utils.StringUtil;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@RestController
@RequestMapping("/eup-approval/approval-flow")
@Slf4j
public class FlowController {

    @Autowired
    private FlowService flowService;

    @Autowired
    private ApprovalModelService approvalModelService;

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String method(Exception e) {
        return "请求参数不合法:\n" + e.getLocalizedMessage();
    }

    /**
     * 创建 flow,
     * 为避免在DB事务
     */
    @PostMapping("/n/createFlow")
    public void createFlow(@RequestBody @Valid CreateFlowIN dto) {
        considerLandingApproval(dto);
        log.info("创建交易,请求参数：{}", dto);
        Flow flow = flowService.createFlow(dto);
        if (FlowStatus.COMPLETED == flow.getFlowStatus()) {
            postComplete(flow.getTransactionNo());
        }
    }

    /**
     * 设置落地复核信息
     */
    private void considerLandingApproval(CreateFlowIN dto) {
        String mainAccountNo = dto.getMainAccountNo();
        if (StringUtil.isEmpty(mainAccountNo))
            mainAccountNo = dto.getAccountNo();
        if (!StringUtil.isEmpty(mainAccountNo)) {
            dto.setInfoForLandingApproval(flowService.getLandingApprovalInfo(dto.getCustomerNo(), mainAccountNo));
        }
    }

    /**
     * 流程完成(COMPLETED)后,通知交易,并更新流程状态
     */
    private void postComplete(String transactionNo) {
        flowService.updateTransactionResult(transactionNo, FlowStatus.TRANSACTION_INPROCESS, null, "复核流程结束");
        flowService.notifyTransaction(transactionNo, FlowStatus.COMPLETED);
    }


    /**
     * 制单查询, 所有交易查询
     */
    @PostMapping("/q/findCreatedFlow")
    public TodoPageOUT findCreatedFlow(@RequestBody @Valid FlowService.FindCreatedFlowIN in) {
        log.info("经办员查询自己制单的流程,参数: {}", in);
        Page<Flow> todoPage = flowService.findCreatedFlow(in);
        return new TodoPageOUT(todoPage, null);
    }

    /**
     * 查询一个复核员所有的待办列表,分页信息
     */
    @PostMapping("/q/todoData")
    public TodoPageOUT findTodoPage(@RequestBody @Valid TodoIN todoIN) {
        log.info("查询待办,参数: {}", todoIN);
        Page<Flow> todoPage = flowService.findTodoPage(todoIN.approverId, todoIN.page, todoIN.size);
        return new TodoPageOUT(todoPage, null);
    }


    /**
     * 单笔复核同意
     */
    @PostMapping("/n/approve")
    public void approve(@RequestBody @Valid ApproveIN dto) {
        log.info("\n 单笔同意,参数={}\n", dto);
        BigDecimal dayLimitUsed = approvalCheck(dto);
        Flow approvedFlow = flowService.approve(dto.transactionNo, dto.approverId, dayLimitUsed);
        if (FlowStatus.COMPLETED == approvedFlow.getFlowStatus()) {
            postComplete(approvedFlow.getTransactionNo());
        }
    }

    private BigDecimal approvalCheck(@RequestBody @Valid FlowController.ApproveIN dto) {
        Flow flow = flowService.findFlow(dto.transactionNo);
        flowService.approvalCheck(flow);
        return flowService.queryDayLimitUsed(flow);
    }

    /**
     * 批量复核同意, 暂时废除
     */
//    @PostMapping("/n/batchApprove")  暂时废除此接口
    public void approve(@RequestBody @Valid BatchApproveIN dto) {
        log.info("批量同意,参数={}", dto);
        List<Flow> completedTransaction = flowService.approve(dto.getTransactionNoList(), dto.approverId);
        completedTransaction.forEach(flow -> flowService.notifyTransaction(flow.getTransactionNo(), flow.getFlowStatus()));
    }


    /**
     * 批量驳回
     */
    @PostMapping("/n/batchReject")
    public void reject(@RequestBody @Valid BatchRejectIN dto) {
        log.info("批量驳回,参数={}", dto);
        if (StringUtil.isEmpty(dto.getRejectReason()) || "".equals(dto.getRejectReason().trim())) {
			throw BusinessUtils.createBusinessException(ErrorCode.HEAD + ErrorCode.PARAMETER_NOT_NULL,"驳回信息");
		}
        List<Flow> rejectTransaction = flowService.reject(dto.getTransactionNoList(), dto.approverId, dto.getRejectReason());
        rejectTransaction.forEach(flow -> flowService.notifyTransaction(flow.getTransactionNo(), flow.getFlowStatus()));
    }

    /**
     * 查询待落地复核,
     *
     * @param in (机构号,制单时间from,制单时间to,第几页,每页多少行)
     */
    @PostMapping("/q/landingTodo")
    public TodoPageOUT findLangdingToDo(@RequestBody @Valid LandingTodoIN in) {
    	log.info("\n 查询待落地复核,参数={}\n", in);
        Page<Flow> landingTodoPage = flowService.findLandingTodo(in.getDepartmentNo(), in.getCreateTimeFrom(), in.getCreateTimeTo(), in.getPage(), in.getSize());
        return new TodoPageOUT(landingTodoPage, null);
    }

    /**
     * 单笔落地复核同意
     */
    @PostMapping("/n/landingApprove")
    public void landingApprove(@RequestBody @Valid LandingApproveIN dto) {
        log.info("\n 落地复核同意,参数={}\n", dto);

        Flow approvedFlow = flowService.landingApprove(dto.transactionNo, dto.tellerId, dto.tellerName);
        if (FlowStatus.COMPLETED == approvedFlow.getFlowStatus()) {
            postComplete(approvedFlow.getTransactionNo());
        }
    }

    /**
     * 落地复核驳回
     */
    @PostMapping("/n/landingReject")
    public void landingReject(@RequestBody @Valid LandingRejectIN dto) {
        log.info("落地复核驳回,参数={}", dto);
        Flow flow = flowService.landingReject(dto.transactionNo, dto.tellerId, dto.tellerName, dto.rejectReason);
        flowService.notifyTransaction(flow.getTransactionNo(), FlowStatus.REJECT);
    }


    /**
     * 已复核查询, 6种过滤条件
     */
    @PostMapping("/q/handledFlow")
    public TodoPageOUT handledFlow(@RequestBody @Valid HandledFlowIN handledFlowIN) {
        log.info("查询已复核,参数={}", handledFlowIN);
        Page<Flow> flowPage = flowService.findHandledFlow(handledFlowIN.approverId, handledFlowIN.condition, handledFlowIN.page, handledFlowIN.size);
        return new TodoPageOUT(flowPage, handledFlowIN.approverId);
    }


    /**
     * 是否还有活动的流程
     */
    @PostMapping("/q/hasActiveFlow")
    public HasActiveFlowOUT hasActiveFlow(@NotNull @RequestBody @NotNull Map<String,String> inAccMap) {
        log.info("查询是否有活动的流程,参数={}", inAccMap);
        return new HasActiveFlowOUT(flowService.hasActiveFlow(inAccMap.get("accountNo")));
    }

    /**
     * 根据客户号和账号删除活动的流程
     */
    @PostMapping("/n/deleteActiveFlowWithCustomerNoAndAccountNo")
    public void deleteActiveFlowWithCustomerNoAndAccountNo(@RequestBody @NotNull Map<String,String> inMap) {
    	log.info("删除还在活动的流程,参数={}", inMap);
        flowService.deleteActiveFlowWithCustomerNoAndAccountNo(inMap);
    }

    /**
     * 同步交易状态
     */
    @PostMapping("/n/refreshTransactionStatus")
    public void refreshTransactionStatus(@RequestBody @Valid RefreshTransactionIN in) {
        log.info("交易服务同步状态到流程,同步信息:{}", in);
        flowService.updateTransactionResult(in.transactionNo, in.transactionStatus, in.code, in.message);
    }


    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class ApproveIN {
        @NotEmpty String approverId;
        @NotEmpty String transactionNo;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class ApproveOUT {
        String flowStatus;
    }

    @Data
    private static class BatchApproveIN {
        @NotNull List<String> transactionNoList;
        @NotNull String approverId;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    @ToString(callSuper = true)
    private static class BatchRejectIN extends BatchApproveIN {
        @NotNull
        String rejectReason;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class TodoIN {
        @NotEmpty
        String approverId;
        @Min(0)
        int page;
        @Min(1)
        int size;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    @ToString(callSuper = true)
    private static class HandledFlowIN extends TodoIN {
        @NotNull
        ConditionIN condition;
    }

    @Data
    private static class HasActiveFlowOUT {

        private final boolean hasActiveFlow;

        public HasActiveFlowOUT(boolean has) {
            this.hasActiveFlow = has;
        }
    }

    @Data
    static class FlowOUT {
        private final String lastApprover;
        private String transactionNo;
        private String functionCode;
        private BigDecimal amount;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime createdTime;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime lastApprovalDate;
        private FlowStatus status;
        private String accountNo;
        private String createdChannelId;
        private String creatorNo;
        private int currentApprovalLevel;
        private Long customerNo;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime handleTime;
        private String rejectReason;
        private Map extraData;
        private String transactionMessage;


        FlowOUT(Flow flow, String approverId) {
            transactionNo = flow.getTransactionNo();
            functionCode = flow.getFunctionCode();
            amount = flow.getTransactionAmount();
            createdTime = flow.getCreateTime();
            lastApprovalDate = flow.getLastApprovalDate();
            status = flow.getFlowStatus();
            accountNo = flow.getAccountNo();
            createdChannelId = flow.getChannelId();
            creatorNo = flow.getCreatorNo();
            currentApprovalLevel = flow.getCurrentLevelIndex();
            customerNo = flow.getCustomerNo();
            extraData = JSON.parseObject(flow.getExtraData(), Map.class);
            lastApprover = flow.getLastApprover();
            handleTime = flow.getHandledTimeOf(approverId);
            rejectReason = flow.getRejectReason();
            transactionMessage = flow.getTransactionMessage();
        }
    }

    /**
     * 用来代表查询（待复核＼已复核＼待落地的）的分页结果集合
     */
    @Data
    static class TodoPageOUT {
        private final long totalElements;
        private final int totalPages;
        private final List<FlowOUT> content;

        /**
         * 只在查询已复核时才需要第二个参数, 为了在结果中包含指定的复核员的复核时间
         */
        public TodoPageOUT(Page<Flow> page, String approverId) {
            content = page.getContent().stream().map(flow -> new FlowOUT(flow, approverId)).collect(Collectors.toList());
            totalElements = page.getTotalElements();
            totalPages = page.getTotalPages();
        }
    }



    @Data
    static class LandingTodoIN {
        @NotNull
        Long departmentNo;
        @NotNull
        @DateTimeFormat(pattern = "yyyy-MM-dd")
        LocalDate createTimeFrom;
        @NotNull
        @DateTimeFormat(pattern = "yyyy-MM-dd")
        LocalDate createTimeTo;
        @Min(0)
        int page;
        @Min(1)
        int size;

    }

    @Data
    static class LandingApproveIN {
        @NotNull
        public String transactionNo;
        @NotNull
        public String tellerId;
        @NotNull
        public String tellerName;
    }

    @Data
    @ToString(callSuper = true)
    @EqualsAndHashCode(callSuper = true)
    static class LandingRejectIN extends LandingApproveIN {
        public String rejectReason;
    }

    @Data
    static class RefreshTransactionIN {
        @NotNull
        String transactionNo;
        @NotNull
        FlowStatus transactionStatus;
        String code;
        String message;
    }
    
    /**
	 * <p> Description:  删除状态为业务已受理的制单流程</p>
	 * 
	 * @param transNo
	 * 
	 * @author wangxiaoxiang
	 */
    @PostMapping("/n/deleteOrderFlow")
    public void deleteOrderFlowWithTransNo(@RequestBody @Valid CreateFlowIN dto) {
        log.info("删除业务已受理的制单流程,参数={}", dto);
        flowService.deleteOrderFlow(dto);
    }
    
    /**
	 * <p> Description:根据客户，删除复核模型和正在处理的交易。
	 * 			如果渠道id列表为空，则删除客户所有的复核模型和正在处理的交易;
	 * 			如果渠道id列表不为空，则删除客户模型相关渠道的所有正在处理交易</p>
	 * 
	 * @param transNo
	 * 
	 * @author songsongtao
	 */
    @PostMapping("/n/deleteModelAndFlowByCustomer")
    public void deleteModelAndFlowByCustomer(@RequestBody CancelCustomerModelFlowDTO dto) {
        log.info("删除制单流程及审核模型,参数={}", dto);
        //删除流程
        flowService.deleteFlowByCustomer(dto.getCustomerNo(), dto.getChannelIds());
        if(dto.getChannelIds() == null || dto.getChannelIds().isEmpty()) {
            //删除模型
            approvalModelService.deleteApprover(new ArrayList<Long>(), dto.getCustomerNo());
        }
    }
    
    /**
	 * <p> Description:  根据操作员列表，删除复核模型和正在处理的交易</p>
	 * 只将操作员渠道为空的操作员用来删除复核模型，如果所有的操作员都有渠道，则不需要删除复合模型
	 * 
	 * @param transNo
	 * 
	 * @author songsongtao
	 */
    @PostMapping("/n/deleteModelAndFlowByUser")
    public void deleteModelAndFlowByUser(@RequestBody CancelUserModelFlowDTO dto) {
        log.info("删除制单流程及审核模型,参数={}", dto);
        //删除流程
        flowService.deleteFlowByCustomerAndUsers(dto.getUsers(), dto.getCustomerNo());
        
        //需要对操作员进行过滤，只有所有渠道都注销的操作员，才需要删除此操作员在模型上的信息
        List<Long> filterUserNos = new ArrayList<Long>();
		dto.getUsers().stream().filter(x -> null == x.getChannelIds() || x.getChannelIds().isEmpty())
				.forEach(x -> filterUserNos.add(x.getUserNo()));
		//如果过滤完没有渠道为空的操作员，说明不需要删除模型
		if(!filterUserNos.isEmpty()) {
	        //删除模型
	        approvalModelService.deleteApprover(filterUserNos, dto.getCustomerNo());
		}
    }
}

