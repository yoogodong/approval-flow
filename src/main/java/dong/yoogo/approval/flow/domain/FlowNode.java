package dong.yoogo.approval.flow.domain;

import dong.yoogo.approval.flow.constant.NodeStatus;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import javax.persistence.*;
import javax.validation.constraints.Digits;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Entity
@Table(name = "TB_OC_APR_FLOWNODE")
@EqualsAndHashCode
@ToString
@Getter
public class FlowNode {


    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE,generator="SEQ_APR_FLOWNODE")
    @SequenceGenerator(name = "SEQ_APR_FLOWNODE",sequenceName="SEQ_APR_FLOWNODE",allocationSize=1)
    private Long id;

    /**
     * 交易额度
     */
    @Setter
    @Digits(integer = 19, fraction = 2)
    @Column(name = "TRANS_AMOUNT")
    private BigDecimal transactionAmount;
    
    /**
     * 日累计对外支付额
     */
    @Setter
    @Column(name = "DAY_OUT_AMOUNT")
    private BigDecimal dayOutAmount;

    /**
     * 节点状态
     */
    @Enumerated(EnumType.STRING)
    private NodeStatus status = NodeStatus.DEFAULT;

    /**当前阶段最小复核次数*/
    @Setter
    private int minApprovalCount;


    private int approvedCount = 0;

    @Setter
    @ElementCollection
    @CollectionTable(name = "TB_OC_APR_FLOWNODE_APPROVER")
    private List<Approver> approverList = new ArrayList<>();


    public List<String> getVisibleApproverIds() {
        List<String> visibleApprover = approverList.stream()
                .filter(x -> x.isVisible(transactionAmount))
                .map(x -> x.getUserNo()).collect(Collectors.toList());
        log.debug("获得可以复核的人员列表:{}", visibleApprover);
        return visibleApprover;
    }


    /**
     * 复核通过
     * @param dayOutLimit 
     */
    public NodeStatus approve(String approverId, BigDecimal dayLimitUsed) {

        Approver approver = getApproverOnThisNode(approverId);

        //节点设置的日累计直接对外支付金额 与 账户当日已用限额比较，如果大于，则可以直接结束节点，否则需要走原有逻辑
        if(null != dayOutAmount && null != dayLimitUsed && (dayLimitUsed.add(transactionAmount)).compareTo(dayOutAmount) <= 0) {
        	status = NodeStatus.END_FLOW;
        }
        else if (approver.couldDeal(transactionAmount)) {
            status = NodeStatus.END_FLOW;
        }
        else if (++approvedCount == minApprovalCount) {
            status = NodeStatus.END_NODE;
        }
        else {
            status = NodeStatus.CONTINUE;
        }
        log.debug("当前节点都过复核, {}", this);
        return status;
    }

    public void reject() {
        status = NodeStatus.REJECT;
        log.debug("当前节点已经被驳回,{}", this);
    }

    public NodeStatus preApproval(String approverId) {
        Approver approver = getApproverOnThisNode(approverId);
        if (approver.couldDeal(transactionAmount))
            return NodeStatus.END_FLOW;
        else if (approvedCount + 1 == minApprovalCount)
            return NodeStatus.END_NODE;
        else
            return NodeStatus.CONTINUE;
    }


    private Approver getApproverOnThisNode(String approverId) {
        return approverList.stream().filter(x -> x.getUserNo().equals(approverId)).findFirst().get();
    }
    
    public boolean containUserNos(List<Long> userNos) {
		for (Approver approver : approverList) {
			long userNo = Long.parseLong(approver.getUserNo());
			if(userNos.contains(userNo)) {
				return true;
			}
		}
		return false;
	}
}
