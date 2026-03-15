package com.flashnote.favorite.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flashnote.favorite.entity.FavoriteMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FavoriteMessageMapper extends BaseMapper<FavoriteMessage> {
}
