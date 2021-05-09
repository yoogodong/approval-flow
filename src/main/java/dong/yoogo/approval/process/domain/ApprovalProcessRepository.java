package dong.yoogo.approval.process.domain;


import java.util.Optional;


public interface ApprovalProcessRepository{
    void save(ApprovalProcess process);

    Optional<ApprovalProcess> findById(String transactionNo);
}
