package dong.yoogo.approval.process.interfaces.dto;

import dong.yoogo.approval.process.domain.ApprovalProcess;

public class CreateProcessIN {
    public ApprovalProcess toEntity() {
        ApprovalProcess process = new ApprovalProcess();
        return process;
    }
}
