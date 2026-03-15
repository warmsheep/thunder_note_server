package com.flashnote.flashnote.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flashnote.flashnote.entity.FlashNote;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FlashNoteMapper extends BaseMapper<FlashNote> {
}
