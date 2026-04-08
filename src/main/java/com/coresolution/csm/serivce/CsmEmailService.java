package com.coresolution.csm.serivce;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CsmEmailService {

    @Autowired
    private JavaMailSender mailSender;

    public void sendPasswordResetLink(
            String to,
            String resetLink,
            String userNumber,
            String userInstitution,
            String userId,
            String instName) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(to);
            helper.setSubject("코어솔루션 카운셀맨 시스템의 비밀번호 설정 안내.");

            String htmlMsg = "<div style='font-family: Arial, sans-serif; max-width: 600px; margin: 0; padding: 20px; border: 1px solid #ddd;'>"
                    + "<h3 style='color: #333; border-bottom: 1px solid #ddd; padding-bottom: 10px;'>코어솔루션 카운셀맨 시스템</h3>"
                    + "<p style='color: #555;'><strong>기관코드 :</strong> " + instName + "</p>"
                    + "<p style='color: #555;'><strong>아이디 :</strong> " + userId + "</p><br>"
                    + "<p style='color: #555;'>비밀번호 설정을 위해 아래 링크를 클릭해 주세요.</p>"
                    + "<p><a href='" + resetLink + "' style='color: #1a73e8; text-decoration: none;'>" + resetLink + "</a></p>"
                    + "<br>"
                    + "<p style='color: #555;'>링크는 24시간 동안 유효합니다. 이 후에는 다시 요청해 주시기 바랍니다.</p>"
                    + "<p style='color: #999; font-size: 12px;'>이 메일은 자동 생성된 메일이므로 회신하지 마세요.</p>"
                    + "</div>";

            helper.setText(htmlMsg, true);
            mailSender.send(message);
        } catch (Exception e) {
            log.error("[sendPasswordResetLink] fail to={}, userId={}, inst={}", to, userId, userInstitution, e);
            throw new RuntimeException("메일 발송 실패", e);
        }
    }
}
