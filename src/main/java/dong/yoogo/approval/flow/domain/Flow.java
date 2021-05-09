package dong.yoogo.approval.flow.domain;

import dong.yoogo.approval.flow.constant.FlowStatus;
import dong.yoogo.approval.flow.constant.NodeStatus;
import dong.yoogo.approval.flow.service.CreateFlowIN;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
     * 流程创建时间， 用以排序, 相当于制单时间
     */
    @Column(name = "CREATE_TIME")
    private LocalDateTime createTime;

    /**
     * 当前审批节点的索引
     */
    private int currentLevelIndex = 0;


    /**
     * 流程状态
     */
    @Enumerated(EnumType.STRING)
    private FlowStatus flowStatus;


    /**
     * 流程的节点，负责控制节点内部的审批逻辑及记录每个审批人的结果
     */
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderColumn
    private List<FlowNode> nodeList = new ArrayList<>();



    public Flow(CreateFlowIN in) {

    }







    /**
     * 复核通过
     *
     * @return 流程是否完成
     */
    public boolean approve() {
        log.info("同意:transactionNo={}", this.getTransactionNo());
        //复核，交给节点控制流程。返回（节点完成、交易完成、继续） 三种状态,记录复核结果（通过、驳回、缺省）和 复核时间 到操作员
        NodeStatus nodeStatus = nodeList.get(currentLevelIndex).approve();
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

        return flowStatus == FlowStatus.COMPLETED;
    }




    /**
     * 复核驳回
     */
    public void reject(String approverId, String rejectReason) {
        log.info("复核驳回:approverId={},,transactionNo={},驳回原因={}", approverId, this.getTransactionNo(), rejectReason);
        nodeList.get(currentLevelIndex).reject();
        flowStatus = FlowStatus.REJECT;
    }



    private void endNode() {
        if (nodeList.size() == currentLevelIndex + 1) {
            log.info("流程已经完成");
            flowStatus = FlowStatus.COMPLETED;
        } else {
            log.info("进入第{}级别复核", currentLevelIndex + 2);
            currentLevelIndex++;
            flowStatus = FlowStatus.IN_PROCESS;
        }
    }

}
