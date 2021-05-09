package dong.yoogo.approval.process.domain;

import dong.yoogo.approval.process.domain.constant.levelStatus;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import java.util.Set;

@Slf4j
@Entity
@Table(name = "TB_OC_APR_FLOWNODE")
@EqualsAndHashCode
@ToString
public class ProcessLevel {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_APR_FLOWNODE")
    @SequenceGenerator(name = "SEQ_APR_FLOWNODE", sequenceName = "SEQ_APR_FLOWNODE", allocationSize = 1)
    private Long id;


    @Enumerated(EnumType.STRING)
    private levelStatus status = levelStatus.DEFAULT;

    /**
     * 当前阶段最小审批次数
     */
    private int minApprovalCount;

    @Getter
    @ElementCollection
    private Set<String> approvers;

    private int approvedCount = 0;


    /**
     * 通过
     */
    public levelStatus approve() {
        if (++approvedCount == minApprovalCount) {
            status = levelStatus.END_LEVEL;
        } else {
            status = levelStatus.CONTINUE;
        }
        log.debug("当前节点都过复核, {}", this);
        return status;
    }

    public void reject() {
        status = levelStatus.REJECT;
        log.debug("当前节点已经被驳回,{}", this);
    }

}
