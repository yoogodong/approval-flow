package dong.yoogo.approval.process.infrastructure;

import dong.yoogo.approval.process.domain.ApprovalProcess;
import dong.yoogo.approval.process.domain.ApprovalProcessRepository;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ApprovalProcessRepositoryJPA extends CrudRepository<ApprovalProcess, String>, ApprovalProcessRepository {

}

