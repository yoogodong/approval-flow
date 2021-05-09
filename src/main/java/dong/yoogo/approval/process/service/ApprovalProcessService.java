package dong.yoogo.approval.process.service;

import dong.yoogo.approval.process.domain.ApprovalProcess;
import dong.yoogo.approval.process.domain.ApprovalProcessRepository;
import dong.yoogo.approval.process.service.exception.ProcessNotFoundException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@AllArgsConstructor
@Service
@Transactional
public class ApprovalProcessService {
    private ApprovalProcessRepository repository;

    /**
     * 新建流程,由交易调用
     */
    public void createApprovalProcess(ApprovalProcess process) {
        repository.save(process);
    }



    /**
     * 同意复核
     *
     * @return
     */
    public ApprovalProcess approve(String transactionNo, String approverId) {
        log.info("单笔复核,transactionNO={},approverId={}", transactionNo, approverId);
        ApprovalProcess process = loadApprovalProcess(transactionNo);
        process.approve();
        if (process.isComplete()) {
            clearAllNodes(process);
        }
        return process;
    }

    /**
     * 批量驳回 相同的驳回理由
     *
     * @return
     */
    public void reject(String transactionNo, String approverId, String rejectReason) {
        ApprovalProcess process = loadApprovalProcess(transactionNo);
        process.reject(approverId, rejectReason);
        clearAllNodes(process);
    }


    private ApprovalProcess loadApprovalProcess(String transactionNo) {
        return repository.findById(transactionNo).orElseThrow(() -> new ProcessNotFoundException());
    }

    /**
     * 流程完成后,删除所有节点
     */
    private void clearAllNodes(ApprovalProcess ApprovalProcess) {
        log.info("流程完成,删除所有节点:{}", ApprovalProcess.getNodeList());
        ApprovalProcess.getNodeList().clear();
    }


}