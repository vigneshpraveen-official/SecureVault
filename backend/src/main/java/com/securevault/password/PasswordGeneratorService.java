package com.securevault.password;

import com.securevault.password.dto.GenerateRequest;
import com.securevault.password.dto.GenerateResponse;

public interface PasswordGeneratorService {

    GenerateResponse generate(GenerateRequest request);
}
