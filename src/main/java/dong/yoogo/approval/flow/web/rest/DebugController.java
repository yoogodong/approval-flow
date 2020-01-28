package dong.yoogo.approval.flow.web.rest;


import dong.yoogo.approval.flow.domain.Flow;
import dong.yoogo.approval.flow.domain.FlowRepository;
import com.querydsl.core.BooleanBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * todo: 仅仅永于调试,发布时请取消
 */
@RestController
@Transactional(readOnly = true)
public class DebugController {

    @Autowired
    FlowRepository repository;

    @PostMapping("/debug/flow")
    public Iterable<Flow> load(@RequestBody Flow condition) {
        QFlow flow = QFlow.flow;
        BooleanBuilder builder = new BooleanBuilder();

        if (!StringUtil.isEmpty(condition.getTransactionNo()))
            builder.and(flow.transactionNo.eq(condition.getTransactionNo()));
        if (!StringUtil.isEmpty(condition.getAccountNo()))
            builder.and(flow.accountNo.eq(condition.getAccountNo()));
        if (condition.getCustomerNo() != null)
            builder.and(flow.customerNo.eq(condition.getCustomerNo()));
        if (!StringUtil.isEmpty(condition.getCreatorNo()))
            builder.and(flow.accountNo.eq(condition.getCreatorNo()));
        if (condition.getFlowStatus() != null)
            builder.and(flow.flowStatus.eq(condition.getFlowStatus()));

        return repository.findAll(builder);
    }

}
