package dong.yoogo.approval.process.domain;

import dong.yoogo.approval.process.domain.constant.ProcessStatus;
import dong.yoogo.approval.process.domain.constant.levelStatus;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.EntityListeners;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.OrderColumn;
import javax.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "TB_OC_APR_FLOW")
@NoArgsConstructor
@EqualsAndHashCode
@EntityListeners(AuditingEntityListener.class)
@ToString
@Slf4j
public class Process {

    @Id
    private String processId;

    private LocalDateTime createTime;

    private int currentLevelIndex = 0;

    @Enumerated(EnumType.STRING)
    private ProcessStatus processStatus;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderColumn
    private List<ProcessLevel> levelList = new ArrayList<>();


    public void approve() {
        log.info("同意:processId={}", processId);
        levelStatus levelStatus = levelList.get(currentLevelIndex).approve();
        switch (levelStatus) {
            case END_LEVEL:
                endLevel();
                break;
            default:
                processStatus = ProcessStatus.IN_PROCESS;
        }
    }


    public void reject(String approverId, String rejectReason) {
        log.info("复核驳回:approverId={},,processId={},驳回原因={}", approverId, processId, rejectReason);
        levelList.get(currentLevelIndex).reject();
        processStatus = ProcessStatus.REJECT;
    }


    private void endLevel() {
        if (levelList.size() == currentLevelIndex + 1) {
            log.info("流程已经完成");
            processStatus = ProcessStatus.COMPLETED;
        } else {
            log.info("进入第{}级别复核", currentLevelIndex + 2);
            currentLevelIndex++;
            processStatus = ProcessStatus.IN_PROCESS;
        }
    }

    public Set<String> getApproversOnCurrentLevel() {
        if (processStatus.equals(ProcessStatus.COMPLETED) || processStatus.equals(ProcessStatus.REJECT))
            return new HashSet<>();
        return levelList.get(currentLevelIndex).getApprovers();
    }
}
