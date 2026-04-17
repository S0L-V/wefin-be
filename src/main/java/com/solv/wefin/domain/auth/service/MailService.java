package com.solv.wefin.domain.auth.service;

public interface MailService {

    void sendVerificationCode(String to, String code);

}