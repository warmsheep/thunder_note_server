package com.flashnote.message.typehandler;

import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.flashnote.message.entity.CardPayload;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

@MappedTypes(CardPayload.class)
@MappedJdbcTypes(JdbcType.OTHER)
public class CardPayloadJsonbTypeHandler extends JacksonTypeHandler {

    public CardPayloadJsonbTypeHandler() {
        super(CardPayload.class);
    }

    public CardPayloadJsonbTypeHandler(Class<?> type) {
        super(type);
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Object parameter, JdbcType jdbcType) throws SQLException {
        ps.setObject(i, toJson(parameter), Types.OTHER);
    }

    @Override
    public Object getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parseJson(rs.getString(columnName));
    }

    @Override
    public Object getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parseJson(rs.getString(columnIndex));
    }

    @Override
    public Object getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parseJson(cs.getString(columnIndex));
    }

    private Object parseJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        return parse(json);
    }
}
