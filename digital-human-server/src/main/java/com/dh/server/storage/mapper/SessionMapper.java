package com.dh.server.storage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dh.server.storage.entity.SessionEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SessionMapper extends BaseMapper<SessionEntity> {
}
