package dong.yoogo.approval.process.web.rest;

import dong.yoogo.approval.process.application.ProcessAs;
import dong.yoogo.approval.process.interfaces.dto.ApproveIN;
import dong.yoogo.approval.process.interfaces.dto.CreateProcessIN;
import dong.yoogo.approval.process.interfaces.dto.RejectIN;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;



@RestController
@NoArgsConstructor
@AllArgsConstructor
@RequestMapping("/approval-process")
@Slf4j
public class ProcessController {

    private ProcessAs service;

    /**
     * 创建
     */
    @PostMapping("/create")
    public void createProcess(@RequestBody  @Validated CreateProcessIN in) {
        service.createApprovalProcess(in.toEntity());
    }


    /**
     * 同意
     */
    @PostMapping("/approve")
    public void approve(@RequestBody  @Validated  ApproveIN in) {
        log.info("\n 单笔同意,参数={}\n", in);
        service.approve(in.getProcessId(), in.getApproverId());
    }

    /**
     * 驳回
     * @param in
     */
    public void reject(@RequestBody  @Validated  RejectIN in){
        service.reject(in.getProcessId(),in.getApproverId(),in.getRejectReason());
    }

}

