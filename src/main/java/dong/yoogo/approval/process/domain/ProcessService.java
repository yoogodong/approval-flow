package dong.yoogo.approval.process.domain;

import dong.yoogo.approval.process.domain.adapter.MailService;
import dong.yoogo.approval.process.domain.adapter.ProcessRepository;
import dong.yoogo.approval.process.domain.adapter.UserService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Stream;

@AllArgsConstructor
@Service
@Transactional
public class ProcessService {
    private ProcessRepository repository;
    private UserService userService;
    private MailService mailService;

    /**
     *
     */
    public void notifyApproverOnExpiredProcess(){
        LocalDateTime expiredTime = LocalDateTime.now().minusYears(1);
        Stream<Process> expiredProcess = repository.findByCreateTimeBefore(expiredTime);
        expiredProcess.forEach(process -> {
            Set<String> approvers = process.getApproversOnCurrentLevel();
            approvers.stream().forEach(approverId->{
                String email = userService.findEmailByUserId(Long.valueOf(approverId));
                mailService.sendMail(email);
            });
        });
    }
}
