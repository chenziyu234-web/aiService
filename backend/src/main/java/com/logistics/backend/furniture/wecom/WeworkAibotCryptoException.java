package com.logistics.backend.furniture.wecom;

/**
 * 企业微信回调加解密失败（签名不匹配、Base64/AES/明文结构错误等）。
 */
public class WeworkAibotCryptoException extends Exception {

    public WeworkAibotCryptoException(String message) {
        super(message);
    }

    public WeworkAibotCryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}
