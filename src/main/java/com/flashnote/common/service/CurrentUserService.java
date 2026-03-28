package com.flashnote.common.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.flashnote.auth.entity.User;
import com.flashnote.auth.mapper.UserMapper;
import com.flashnote.common.exception.BusinessException;
import com.flashnote.common.response.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 统一从数据库查询当前用户上下文。
 * <p>
 * 注意：本服务采用 username 参数模式（由调用方从请求中提取），
 * 不直接操作 SecurityContextHolder。
 * </p>
 */
@Service
public class CurrentUserService {

    private final UserMapper userMapper;

    @Autowired
    public CurrentUserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /**
     * 根据用户名获取用户 ID。
     *
     * @param username 用户名（不能为空）
     * @return 用户 ID
     * @throws BusinessException 用户不存在时抛出 UNAUTHORIZED
     */
    public Long getRequiredUserId(String username) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "User not found");
        }
        return user.getId();
    }

    /**
     * 根据用户名获取完整的 User 实体。
     *
     * @param username 用户名（不能为空）
     * @return User 实体
     * @throws BusinessException 用户不存在时抛出 UNAUTHORIZED
     */
    public User getRequiredUser(String username) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "User not found");
        }
        return user;
    }
}
