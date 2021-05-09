package dong.yoogo.approval.process.interfaces.dto;

import dong.yoogo.approval.process.domain.Process;

public class CreateProcessIN {
    public Process toEntity() {
        Process process = new Process();
        return process;
    }
}
