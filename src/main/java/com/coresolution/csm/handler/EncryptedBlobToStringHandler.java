package com.coresolution.csm.handler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

@MappedJdbcTypes(JdbcType.BLOB)
@MappedTypes(String.class)
public class EncryptedBlobToStringHandler extends BaseTypeHandler<String> {

  @Override
  public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
    byte[] enc = Crypto.encryptToBytes(parameter); // 평문 -> 바이트 암호문
    ps.setBytes(i, enc);
  }

  @Override
  public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
    byte[] cipher = rs.getBytes(columnName);
    if (cipher == null) return null;
    return Crypto.decryptToString(cipher);        // 바이트 암호문 -> 평문
  }

  @Override public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    byte[] cipher = rs.getBytes(columnIndex);
    if (cipher == null) return null;
    return Crypto.decryptToString(cipher);
  }

  @Override public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
    byte[] cipher = cs.getBytes(columnIndex);
    if (cipher == null) return null;
    return Crypto.decryptToString(cipher);
  }
}