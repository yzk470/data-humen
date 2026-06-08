package com.dh.server.storage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dh.server.storage.entity.MessageEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MessageMapper extends BaseMapper<MessageEntity> {
}
