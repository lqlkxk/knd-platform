package com.kernel.oauth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kernel.common.constant.CommonConstant;
import com.kernel.common.lock.DistributedLock;
import com.kernel.common.redis.template.RedisRepository;
import com.kernel.common.constant.SecurityConstants;
import com.kernel.common.model.PageResult;
import com.kernel.common.model.Result;
import com.kernel.common.service.impl.SuperServiceImpl;
import com.kernel.oauth.mapper.ClientMapper;
import com.kernel.oauth.model.Client;
import com.kernel.oauth.service.IClientService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * @author knd
 */
@Slf4j
@Service
public class ClientServiceImpl extends SuperServiceImpl<ClientMapper, Client> implements IClientService {
    private final static String LOCK_KEY_CLIENTID = CommonConstant.LOCK_KEY_PREFIX+"clientId:";

    @Autowired
    private RedisRepository redisRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DistributedLock lock;

    @Override
    public Result saveClient(Client client) {
        client.setClientSecret(passwordEncoder.encode(client.getClientSecretStr()));
        String clientId = client.getClientId();
        super.saveOrUpdateIdempotency(client, lock
                , LOCK_KEY_CLIENTID+clientId
                , new QueryWrapper<Client>().eq("client_id", clientId)
                , clientId + "已存在");
        return Result.succeed("操作成功");
    }

    @Override
    public PageResult<Client> listClent(Map<String, Object> params, boolean isPage) {
        Page<Client> page;
        if (isPage) {
            page = new Page<>(MapUtils.getInteger(params, "page"), MapUtils.getInteger(params, "limit"));
        } else {
            page = new Page<>(1, -1);
        }
        List<Client> list = baseMapper.findList(page, params);
        page.setRecords(list);
        return PageResult.<Client>builder().data(list).code(0).count(page.getTotal()).build();
    }

    @Override
    public void delClient(long id) {
        String clientId = baseMapper.selectById(id).getClientId();
        baseMapper.deleteById(id);
        redisRepository.del(clientRedisKey(clientId));
    }

    private String clientRedisKey(String clientId) {
        return SecurityConstants.CACHE_CLIENT_KEY + ":" + clientId;
    }
}
