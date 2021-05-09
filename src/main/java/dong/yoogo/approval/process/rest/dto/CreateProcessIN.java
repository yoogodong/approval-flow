package dong.yoogo.approval.process.rest.dto;

import dong.yoogo.approval.process.domain.ApprovalProcess;

public class CreateProcessIN {
    public ApprovalProcess toEntity() {
        ApprovalProcess process = new ApprovalProcess();
        return process;
    }
}
