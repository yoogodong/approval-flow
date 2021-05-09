package dong.yoogo.approval.process.application;

import dong.yoogo.approval.process.application.exception.ProcessNotFoundException;
import dong.yoogo.approval.process.domain.Process;
import dong.yoogo.approval.process.domain.adapter.ProcessRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Stream;

@Slf4j
@AllArgsConstructor
@Service
@Transactional
public class ProcessAs {
    private ProcessRepository repository;


    public void createApprovalProcess(Process process) {
        repository.save(process);
    }

    public void approve(String transactionNo, String approverId) {
        loadApprovalProcess(transactionNo).approve();
    }

    public void batchApprove(String approverId,String... processId){
        Stream.of(processId).forEach(id->{
            loadApprovalProcess(id).approve();
        });
    }

    public void reject(String transactionNo, String approverId, String rejectReason) {
        loadApprovalProcess(transactionNo).reject(approverId, rejectReason);
    }


    private Process loadApprovalProcess(String transactionNo) {
        return repository.findById(transactionNo).orElseThrow(ProcessNotFoundException::new);
    }
}