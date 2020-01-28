package dong.yoogo.approval.flow.domain;

import dong.yoogo.approval.flow.constant.FlowStatus;

import java.util.List;

import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.data.repository.CrudRepository;

public interface FlowRepository extends CrudRepository<Flow, String>, QuerydslPredicateExecutor<Flow> {

    void deleteByAccountNoAndFlowStatus(String accountNo, FlowStatus flowStatus);

    int countByAccountNoAndFlowStatus(String accountNo, FlowStatus flowStatus);
    
    List<Flow> findByCustomerNoAndFlowStatusIn(Long customerNo,List<FlowStatus> flowStatus);
    
    void deleteByCustomerNo(Long customerNo);
    
    void deleteByCustomerNoAndChannelIdIn(Long customerNo, List<String> channelIds);

    void deleteByCustomerNoAndAccountNoAndFlowStatusIn(Long customerNo, String accountNo, List<FlowStatus> flowStatus);
}
