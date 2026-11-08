package com.example.webapp.security;

import com.example.webapp.catalog.CatalogException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.security.SecureRandom;
import java.time.*;
import static org.assertj.core.api.Assertions.*;

class SecurityRulesTests {
    static class MutableClock extends Clock {
        long millis;
        public ZoneId getZone(){return ZoneOffset.UTC;} public Clock withZone(ZoneId zone){return this;}
        public Instant instant(){return Instant.ofEpochMilli(millis);} public long millis(){return millis;}
    }
    @Test void captchaExpiresAndIsOneTimeEvenAfterWrongAnswer() throws Exception {
        var clock=new MutableClock();var captcha=new CaptchaService(new SecureRandom(){public int nextInt(int bound){return 0;}},clock);
        var session=new MockHttpSession();byte[] png=captcha.issue(session);
        assertThat(png).startsWith((byte)137,(byte)80,(byte)78,(byte)71);
        clock.millis=120_000;assertThat(captcha.consume(session,"AAAAA")).isFalse();
        captcha.issue(session);assertThat(captcha.consume(session,"wrong")).isFalse();assertThat(captcha.consume(session,"AAAAA")).isFalse();
        captcha.issue(session);assertThat(captcha.consume(session,"aaaaa")).isTrue();assertThat(captcha.consume(session,"AAAAA")).isFalse();
    }
    @Test void rateLimitsSurviveNewSessionsAndExpireByTime() {
        var clock=new MutableClock();var limiter=new AttemptLimiter(clock);
        for(int i=0;i<10;i++)assertThat(limiter.allow("user:alice",10,600_000)).isTrue();
        assertThat(limiter.allow("user:alice",10,600_000)).isFalse();
        assertThat(limiter.allow("user:bob",10,600_000)).isTrue();
        clock.millis=600_000;assertThat(limiter.allow("user:alice",10,600_000)).isTrue();
    }
    @Test void loginGuardLimitsAccountAcrossDifferentSessionsAndAddresses() throws Exception {
        var clock=new MutableClock();var captcha=new CaptchaService(new SecureRandom(){public int nextInt(int bound){return 0;}},clock);
        var filter=new LoginGuardFilter(captcha,new AttemptLimiter(clock));
        for(int i=0;i<11;i++) {
            var request=new MockHttpServletRequest("POST","/api/auth/login");request.setServletPath("/api/auth/login");
            request.setRemoteAddr("192.0.2."+(i+1));request.addParameter("username","alice");request.addParameter("password","any");request.addParameter("captcha","AAAAA");
            captcha.issue(request.getSession());var response=new MockHttpServletResponse();
            filter.doFilter(request,response,(req,res)->((jakarta.servlet.http.HttpServletResponse)res).setStatus(204));
            assertThat(response.getStatus()).isEqualTo(i<10?204:429);
        }
    }
    @Test void invalidCaptchaAttemptsAreRateLimitedByAddress() throws Exception {
        var filter=new LoginGuardFilter(new CaptchaService(),new AttemptLimiter(new MutableClock()));
        for(int i=0;i<31;i++) {
            var request=new MockHttpServletRequest("POST","/api/auth/login");request.setServletPath("/api/auth/login");
            request.addParameter("username","different"+i);request.addParameter("captcha","wrong");
            var response=new MockHttpServletResponse();
            filter.doFilter(request,response,(req,res)->{throw new AssertionError("Must not authenticate");});
            assertThat(response.getStatus()).isEqualTo(i<30?400:429);
        }
    }
    @ParameterizedTest
    @ValueSource(strings={"Short!A1", "alllowercase!12345", "ALLUPPERCASE!12345", "WithoutNumbers!!", "WithoutSymbols12345", "A!password999999", "A!qwerty999999999", "UserName!Random99"})
    void rejectsWeakPasswords(String value) { assertThatThrownBy(()->PasswordPolicy.validate(value,"username")).isInstanceOf(CatalogException.class); }
    @Test void acceptsLongUnicodePassphraseWithoutTruncatingAndRejectsTooLong() {
        assertThatCode(()->PasswordPolicy.validate("آسمان و دریا!A9hello","username")).doesNotThrowAnyException();
        assertThatCode(()->PasswordPolicy.validate("A!8"+"x".repeat(125),"username")).doesNotThrowAnyException();
        assertThatThrownBy(()->PasswordPolicy.validate("A!8"+"x".repeat(126),"username")).isInstanceOf(CatalogException.class);
    }
}
