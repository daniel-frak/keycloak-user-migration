package com.danielfrak.code.keycloak.providers.rest.remote;

import java.util.Objects;

public class LegacyTotp {
    
    private String secret;
    private String name;
    private int digits;
    private int period;
    private String algorithm;
    private String encoding;

    public String getSecret() {
        return this.secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getDigits() {
        return this.digits;
    }

    public void setDigits(int digits) {
        this.digits = digits;
    }

    public int getPeriod() {
        return this.period;
    }

    public void setPeriod(int period) {
        this.period = period;
    }

    public String getAlgorithm() {
        return this.algorithm;
    }

    public void setAlgorithm(String algorith) {
        this.algorithm = algorith;
    }

    public String getEncoding() {
        return this.encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }
 
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        LegacyTotp legacyTotp = (LegacyTotp) o;

        return Objects.equals(secret, legacyTotp.secret) &&
               Objects.equals(name, legacyTotp.name) &&
               Objects.equals(algorithm, legacyTotp.algorithm) &&
               Objects.equals(encoding, legacyTotp.encoding) &&
               digits == legacyTotp.digits &&
               period == legacyTotp.period;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, secret, digits, period, algorithm, encoding);
    }
}
