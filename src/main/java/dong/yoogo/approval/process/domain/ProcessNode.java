package dong.yoogo.approval.process.domain;

import dong.yoogo.approval.process.domain.constant.NodeStatus;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

@Slf4j
@Entity
@Table(name = "TB_OC_APR_FLOWNODE")
@EqualsAndHashCode
@ToString
@Getter
public class ProcessNode {


    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE,generator="SEQ_APR_FLOWNODE")
    @SequenceGenerator(name = "SEQ_APR_FLOWNODE",sequenceName="SEQ_APR_FLOWNODE",allocationSize=1)
    private Long id;

    /**
     * 节点状态
     */
    @Enumerated(EnumType.STRING)
    private NodeStatus status = NodeStatus.DEFAULT;

    /**当前阶段最小复核次数*/
    @Setter
    private int minApprovalCount;


    private int approvedCount = 0;



    /**
     * 复核通过
     */
    public NodeStatus approve() {
       if (++approvedCount == minApprovalCount) {
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

}
