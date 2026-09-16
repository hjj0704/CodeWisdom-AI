package com.codewisdom.resource.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codewisdom.common.api.ErrorCode;
import com.codewisdom.common.exception.BizException;
import com.codewisdom.resource.auth.AuthTokenService;
import com.codewisdom.resource.auth.AuthTokenService;
import com.codewisdom.resource.dto.AuthResponse;
import com.codewisdom.resource.dto.LoginRequest;
import com.codewisdom.resource.dto.RegisterRequest;
import com.codewisdom.resource.entity.UserEntity;
import com.codewisdom.resource.mapper.UserMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserMapper userMapper;
    private final AuthTokenService tokenService;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(UserMapper userMapper, AuthTokenService tokenService) {
        this.userMapper = userMapper;
        this.tokenService = tokenService;
    }

    public AuthResponse register(RegisterRequest request) {
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getUsername, request.username()));
        if (exists != null && exists > 0) {
            throw BizException.of(ErrorCode.AUTH_USERNAME_EXISTS);
        }

        UserEntity user = new UserEntity();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname() == null || request.nickname().isBlank()
                ? request.username()
                : request.nickname().trim());
        userMapper.insert(user);

        return toAuthResponse(user);
    }

    public AuthResponse login(LoginRequest request) {
        UserEntity user = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getUsername, request.username()));
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw BizException.of(ErrorCode.AUTH_BAD_CREDENTIALS);
        }
        return toAuthResponse(user);
    }

    public UserEntity requireUser(long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.of(ErrorCode.UNAUTHORIZED, "登录已失效，请重新登录");
        }
        return user;
    }

    private AuthResponse toAuthResponse(UserEntity user) {
        String token = tokenService.issueToken(user.getId());
        return new AuthResponse(token, user.getId(), user.getUsername(), user.getNickname());
    }
}
