package dong.yoogo.approval.flow.domain;

import dong.yoogo.approval.flow.constant.ApproverResult;
import dong.yoogo.approval.flow.constant.ErrorCode;
import dong.yoogo.approval.flow.constant.FlowStatus;
import dong.yoogo.approval.flow.constant.NodeStatus;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import javax.persistence.*;
import javax.validation.constraints.Digits;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 审批流程
 * 控制审批流程
 * 查询待办
 * 复核
 */
@Entity
@Table(name = "TB_OC_APR_FLOW")
@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
@EntityListeners(AuditingEntityListener.class)
@ToString
@Slf4j
public class Flow {


    /**
     * 流水号
     */
    @Id
    @Column(name = "TRANS_NO")
    private String transactionNo;
    /**
     * 制单人 userNO
     */
    @NonNull
    private String creatorNo;

    /**
     * 客户
     */
    @NonNull
    @Column(name = "CUSTOMER_NO")
    private Long customerNo;
    /**
     * 渠道
     */
    private String channelId;
    /**
     * 账户
     */
    private String accountNo;
    /**
     * 功能码，用以按交易类型分组
     */
    private String functionCode;

    /**
     * 流程创建时间， 用以排序, 相当于制单时间
     */
    @Column(name = "CREATE_TIME")
    private LocalDateTime createTime;

    /**
     * 当前审批节点的索引
     */
    private int currentLevelIndex = 0;
    /**
     * 交易金额
     */
    @Digits(integer = 19, fraction = 2)
    @Column(name = "TRANS_AMOUNT")
    private BigDecimal transactionAmount;

    /**
     * 流程状态
     */
    @Enumerated(EnumType.STRING)
    private FlowStatus flowStatus;

    /**
     * 当前审批节点的所有审核人列表
     */
    @ElementCollection()
    @CollectionTable(name = "TB_OC_APR_FLOW_APPROVER", joinColumns = {@JoinColumn(name = "FLOW_ID")})
    @Column(name = "APPROVER_NO")
    private List<String> approverList;
    /**
     * 已审批/驳回过的人员列表
     */
    @ElementCollection()
    @CollectionTable(name = "TB_OC_APR_FLOW_HANDLED_APPROVE", joinColumns = {@JoinColumn(name = "FLOW_ID")})
    private List<HandledApprover> handledApproverList = new ArrayList<>();


    /**
     * 流程的节点，负责控制节点内部的审批逻辑及记录每个审批人的结果
     */
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderColumn
    private List<FlowNode> nodeList = new ArrayList<>();

    /**
     * 交易的其他数据,被保存成一个 json 串
     */
    @Column(name = "EXTRA_DATA", length = 4000)
    private String extraData;

    @Version
    @Column(name = "VERSION")
    private long version;

    /**
     * 最后一个复核人, 查询落地复核列表时需要, 为了性能考虑, 增加这个字段而不是从复核人列表中取
     */
    @Column(name = "LAST_APPROVER")
    private String lastApprover;


    @Embedded
    @Getter(AccessLevel.PRIVATE)
    private LandingAppoval landingAppoval;

    /**
     * 交易错误码
     */
    @Column(name = "RET_CODE")
    private String retCodeFromTransaction;
    /**
     * 交易错误信息
     */
    @Column(name = "RET_MSG")
    private String transactionMessage;

    /**
     * 主账户
     */
    @Column(name = "PARENT_ACC_NO")
    private String parentAccountNo;

    /**
     * 复核完成后的回调url，应是完整路径（包含http://）
     */
    @Column(name = "CALL_BACK_URL")
    private String callBackUrl;
    
	/* 创建时间，用于表分区 */
	@Column(updatable = false, name = "CREATE_DATE")
	@CreatedDate
	private Date createDate;
	
	
	/* 最后复核时间 */
	@Column(name = "LAST_APPROVAL_DATE")
	private LocalDateTime lastApprovalDate;


    public Flow(CreateFlowIN in) {
        log.debug("初始化一个复核流程:参数={}", in);
        transactionNo = in.getTransactionNo();
        customerNo = in.getCustomerNo();
        channelId = in.getChannelId();
        accountNo = in.getAccountNo();
        functionCode = in.getFunctionCode();
        creatorNo = in.getCreatorNo();
        transactionAmount = in.getAmount();
        createTime = LocalDateTime.now();
        flowStatus = FlowStatus.WAIT_FOR_REVIEW;
        parentAccountNo = in.getMainAccountNo();
        extraData = in.getExtraData();
        callBackUrl = in.getCallBackUrl();
        landingAppoval = new LandingAppoval(in.getDepartmentNo(), in.getLandingLimit());
    }


    /**
     * 将用户定义过的审批模式阶段列表设置进来
     */
    public void useSpecifiedFlow(List<ApprovalNodeDTO> modelNodeList) {
        log.debug("采用这个审批流程:{}", modelNodeList);
        for (ApprovalNodeDTO modelNode : modelNodeList) {
            nodeList.add(transfer(modelNode));
        }
        updateApproverList();
    }

    private FlowNode transfer(ApprovalNodeDTO modelNode) {
        FlowNode flowNode = new FlowNode();
        flowNode.setTransactionAmount(transactionAmount);

        flowNode.setMinApprovalCount(modelNode.getApprovalCount());

        List<Approver> approvers = modelNode.getApprovalUsers().stream().map(x -> {
            Long userNo = x.getUserNo();
            BigDecimal minLimit = x.getMinLimit();
            BigDecimal maxLimit = x.getMaxLimit();
            BigDecimal maxOutLimit = x.getMaxOutLimit();
            return new Approver(userNo.toString(), minLimit, maxLimit, maxOutLimit);
        }).collect(Collectors.toList());
        flowNode.setDayOutAmount(modelNode.getDayOutLimit());
        flowNode.setApproverList(approvers);
        log.debug("将审批模式节点{},转化为流程节点{}", modelNode, flowNode);
        return flowNode;
    }


    private void updateApproverList() {
        log.info("即将更新审批人列表,当前{}", approverList);
        if (nodeList.size() > 0) {
            FlowNode flowNode = nodeList.get(currentLevelIndex);
            approverList = flowNode.getVisibleApproverIds();
            log.info("已经更新审批人列表,当前{}", approverList);
        }
    }

    /**
     * 查询对指定的复核员来说, 这是不是最后一笔复核
     */
    public boolean isLastDeal(String approverId) {
        log.info("查询经{}复核后,流程是否就完成了", approverId);
        checkValidResponse(approverId);
        
        // 流程节点总数小于需要操作的节点，提示'该笔交易已提交，请勿重复操作'.(需要操作的节点下标从0开始，所以需要加1)
        if(null == nodeList || (nodeList.size() < currentLevelIndex + 1)) {
        	throw BusinessUtils.createBusinessException(ErrorCode.HEAD + ErrorCode.ALREADY_SUBMIT);
        }
        NodeStatus nodeStatus = nodeList.get(currentLevelIndex).preApproval(approverId);
        boolean nodeEnd = NodeStatus.END_NODE == nodeStatus;
        boolean lastLevel = nodeList.size() == currentLevelIndex + 1;
        boolean deal = nodeStatus == NodeStatus.END_FLOW;

        return nodeEnd && lastLevel || deal;
    }

    /**
     * 复核通过
     *
     * @return 流程是否完成
     */
    public boolean approve(String approverId, BigDecimal dayLimitUsed,Boolean isLandingAppoval) {
        log.info("复核同意:approverId={},transactionNo={}", approverId, this.getTransactionNo());
        // 设置最后复核时间
        lastApprovalDate = LocalDateTime.now();
        
        checkValidResponse(approverId);
        
        // 流程节点总数小于需要操作的节点，提示'该笔交易已提交，请勿重复操作'.(需要操作的节点下标从0开始，所以需要加1)
		if(null == nodeList || (nodeList.size() < currentLevelIndex + 1)) {
			throw BusinessUtils.createBusinessException(ErrorCode.HEAD + ErrorCode.ALREADY_SUBMIT);
		}
        
        //复核，交给节点控制流程。返回（节点完成、交易完成、继续） 三种状态,记录复核结果（通过、驳回、缺省）和 复核时间 到操作员
        NodeStatus nodeStatus = nodeList.get(currentLevelIndex).approve(approverId, dayLimitUsed);
        switch (nodeStatus) {
            case END_FLOW:
                flowStatus = FlowStatus.COMPLETED;
                break;
            case END_NODE:
                endNode();
                break;
            default:
                flowStatus = FlowStatus.IN_PROCESS;
        }

        updateApprover(approverId, ApproverResult.APPROVED, null);

        //白名单 校验落地复核 true：不在白名单，false：在白名单
        log.info("落地复核是否在白名单："+isLandingAppoval + "----true：不在白名单，false：在白名单");
        if(isLandingAppoval) {
			if (null != landingAppoval && flowStatus == FlowStatus.COMPLETED
	        		&& landingAppoval.needLandingApproval(transactionAmount)) {
	            flowStatus = FlowStatus.WAIT_FOR_LANDING_REVIEW;
	        }
		}
        return flowStatus == FlowStatus.COMPLETED;
    }

    private void updateApprover(String approverId, ApproverResult result, String rejectReason) {
        //把当前复核员从当前复核人列表中移动到已复核人员列表中
        approverList.remove(approverId);
        lastApprover = approverId;
        //        添加复核员到已响应的复核员列表中
        HandledApprover handledApprover = new HandledApprover(approverId, LocalDateTime.now(), result);
        handledApprover.setRejectReason(rejectReason);
        setNoBusiField(handledApprover);
        handledApproverList.add(handledApprover);
    }

    /**
     * 设置非业务字段
     *
     * @param handledApprover
     */
    private void setNoBusiField(HandledApprover handledApprover) {
        JSONObject parseObject = null;
        try {
            parseObject = JSON.parseObject(CurrentHeaderHolder.getCurrentTellerID());
        } catch (JSONException e) {
            log.warn("无法解析 tellerId , 实际值为{}", CurrentHeaderHolder.getCurrentTellerID());
            return;
        }
        String clientIp = (String) parseObject.get("clientIp");
        Object oriConsumer = CurrentHeaderHolder.getCurrentZyHeader().get("oriConsumer");
        if (null == oriConsumer) {
            log.error("无法获取复核渠道");
        } else {
            handledApprover.setChannelId(oriConsumer.toString());
        }
        handledApprover.setClientIp(clientIp);
        try {
            String hostName = InetAddress.getLocalHost().getHostName();
            String hostAddress = InetAddress.getLocalHost().getHostAddress();
            handledApprover.setTranServiceName(hostName);
            handledApprover.setTranServiceIp(hostAddress);
        } catch (UnknownHostException e) {
            log.error("无法获取当前服务器名称或IP", e);
        }

    }

    /**
     * 复核驳回
     */
    public void reject(String approverId, String rejectReason) {
        log.info("复核驳回:approverId={},,transactionNo={},驳回原因={}", approverId, this.getTransactionNo(), rejectReason);
        // 设置最后复核时间
        lastApprovalDate = LocalDateTime.now();
        
        checkValidResponse(approverId);
		
        // 流程节点总数小于需要操作的节点，提示'该笔交易已提交，请勿重复操作'.(需要操作的节点下标从0开始，所以需要加1)
		if(null == nodeList || (nodeList.size() < currentLevelIndex + 1)) {
			throw BusinessUtils.createBusinessException(ErrorCode.HEAD + ErrorCode.ALREADY_SUBMIT);
		}
        nodeList.get(currentLevelIndex).reject();
        flowStatus = FlowStatus.REJECT;
        //把当前复核员从当前复核人列表中移动到已相应人员列表中
        updateApprover(approverId, ApproverResult.REJECT, rejectReason);
    }


    private void checkValidResponse(String approverId) {
        if (!(flowStatus == FlowStatus.WAIT_FOR_REVIEW || flowStatus == FlowStatus.IN_PROCESS)) { //不可复核
            log.warn("当前流程的状态处于 {},不可以复核", this.flowStatus);
            throw BusinessUtils.createBusinessException(ErrorCode.HEAD + ErrorCode.FLOW_ENDED, flowStatus);
        } else if (!approverList.contains(approverId)) {  //必须在复核员列表中
            log.warn("复核人{},不在列表中{}", approverId, approverList);
            throw BusinessUtils.createBusinessException(ErrorCode.HEAD + ErrorCode.NOT_ALLOWED);
        } else if (approverId.equals(creatorNo)) {   //检查是不是此复核员同时是制单员
            log.warn("制单员企图自己复核{}", approverId);
            throw BusinessUtils.createBusinessException(ErrorCode.HEAD + ErrorCode.SELF_APPROVAL);
        }
    }

    private void endNode() {
        if (nodeList.size() == currentLevelIndex + 1) {
            log.info("流程已经完成");
            flowStatus = FlowStatus.COMPLETED;
        } else {
            log.info("进入第{}级别复核", currentLevelIndex + 2);
            currentLevelIndex++;
            flowStatus = FlowStatus.IN_PROCESS;

            updateApproverList();
        }
    }


    /**
     * 零级复核
     */
    public void zeroNode() {
        if (landingAppoval.needLandingApproval(transactionAmount))
            flowStatus = FlowStatus.WAIT_FOR_LANDING_REVIEW;
        else
            flowStatus = FlowStatus.COMPLETED;
    }

    /**
     * 交易回调更新状态
     *
     * @param transactionResult
     * @param code              核心错误码
     * @param message           错误消息
     */
    public void transactionResult(FlowStatus transactionResult, String code, String message) {
        log.info("交易更新状态到流程: trasnaction result: {}, code:{}, message:{}", transactionResult, code, message);
        flowStatus = transactionResult;
        this.retCodeFromTransaction = code;
        this.transactionMessage = message;
    }

    /**
     * 落地复核同意
     */
    public void landingApprove(String tellerId, String tellerName) {
        landingAppoval.approve(tellerId, tellerName);
        flowStatus = FlowStatus.COMPLETED;
    }

    /**
     * 落地复核驳回
     */
    public void landingReject(String tellerId, String tellerName, String rejectReason) {
        landingAppoval.reject(tellerId, tellerName, rejectReason);
        flowStatus = FlowStatus.REJECT;
    }

    /**
     * 获得驳回原因,要么是复核驳回,要么是落地复核驳回
     */
    public String getRejectReason() {
        if (!flowStatus.equals(FlowStatus.REJECT))
            return null;
        String rejectReason = landingAppoval.getRejectReason();
        if (StringUtil.isEmpty(rejectReason)) {
            rejectReason = handledApproverList.stream().filter(x -> x.getResult() == ApproverResult.REJECT)
                    .map(x -> x.getRejectReason()).findFirst().orElse(null);
        }
        return rejectReason;
    }

    /**
     * 为了获得当前复核员的复核时间:从复核员列表中取出当前复核员,获取其时间
     */
    public LocalDateTime getHandledTimeOf(String approverId) {
        if (approverId == null)
            return null;
        return handledApproverList.stream()
                .filter(x -> x.getUserNo().equals(approverId))
                .map(x -> x.getHandleTime())
                .findFirst().orElse(null);
    }

    /**
     * 是否账户相关
     */
    public boolean isAccountOf() {
        return accountNo != null;
    }
    
	public boolean conatainUserNos(List<UserModelFlowDTO> userNos) {
		//需要从当前节点之后的节点开始查找，即所有未处理的节点
		for (int i = currentLevelIndex; i < nodeList.size(); i++) {
			FlowNode flowNode = nodeList.get(i);
			List<Long> filterUserNos = new ArrayList<Long>();
			//需要将该操作员列表中的包含该交易渠道的操作员过滤出来，来进行操作员比较，如果渠道为空，则说明是注销操作员，也需要进行比较
			userNos.stream().filter(x -> null == x.getChannelIds() || x.getChannelIds().isEmpty()
					|| x.getChannelIds().contains(channelId)).forEach(x -> filterUserNos.add(x.getUserNo()));
			if(flowNode.containUserNos(filterUserNos)) {
				return true;
			}
		}
		return false;
	}
}
