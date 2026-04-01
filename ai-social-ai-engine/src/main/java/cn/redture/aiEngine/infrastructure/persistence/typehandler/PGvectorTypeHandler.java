package cn.redture.aiEngine.infrastructure.persistence.typehandler;

import com.pgvector.PGvector;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;
import org.postgresql.util.PGobject;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * PGvector TypeHandler for List<Double>
 */
@MappedTypes(List.class)
public class PGvectorTypeHandler extends BaseTypeHandler<List<Double>> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<Double> parameter, JdbcType jdbcType) throws SQLException {
        if (parameter == null || parameter.isEmpty()) {
            ps.setNull(i, java.sql.Types.OTHER);
            return;
        }
        float[] floats = new float[parameter.size()];
        for (int j = 0; j < parameter.size(); j++) {
            floats[j] = parameter.get(j).floatValue();
        }
        ps.setObject(i, new PGvector(floats));
    }

    @Override
    public List<Double> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parseVector(rs.getObject(columnName));
    }

    @Override
    public List<Double> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parseVector(rs.getObject(columnIndex));
    }

    @Override
    public List<Double> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parseVector(cs.getObject(columnIndex));
    }

    private List<Double> parseVector(Object obj) {
        if (obj == null) {
            return null;
        }

        if (obj instanceof PGvector) {
            float[] floats = ((PGvector) obj).toArray();
            if (floats != null) {
                List<Double> list = new ArrayList<>();
                for (float f : floats) {
                    list.add((double) f);
                }
                return list;
            }
        }

        String value = null;
        if (obj instanceof PGobject) {
            value = ((PGobject) obj).getValue();
        } else if (obj instanceof String) {
            value = (String) obj;
        } else {
            value = obj.toString();
        }

        if (value == null) {
            return null;
        }

        // Parse string format "[1.0,2.0,...]"
        value = value.replace("[", "").replace("]", "");
        if (value.trim().isEmpty()) {
            return new ArrayList<>();
        }

        String[] parts = value.split(",");
        List<Double> list = new ArrayList<>();
        for (String part : parts) {
            try {
                list.add(Double.parseDouble(part.trim()));
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        return list;
    }
}
