package com.flashnote.collection.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flashnote.collection.entity.Collection;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CollectionMapper extends BaseMapper<Collection> {
}
