package com.persistence.mapper;

import com.persistence.model.AuthLogRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuthLogMapper
{
    void insert(AuthLogRecord record);
}
