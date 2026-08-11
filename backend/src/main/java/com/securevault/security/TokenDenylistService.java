package com.securevault.security;

import java.time.Duration;

public interface TokenDenylistService {

    void denylist(String jti, Duration ttl);

    boolean isDenylisted(String jti);
}
