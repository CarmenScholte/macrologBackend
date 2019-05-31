package csl.database;

import csl.database.model.Portion;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;


@Repository
public class PortionRepository {
    public static final String TABLE_NAME = "portion";

    static final String COL_ID = "id";
    private static final String COL_FOOD_ID = "food_id";
    private static final String COL_DESCRIPTION = "description";
    private static final String COL_GRAMS = "grams";

    public static final String TABLE_CREATE =
            "CREATE TABLE " + TABLE_NAME + " (" +
                    COL_ID + " INT(6) PRIMARY KEY AUTO_INCREMENT, " +
                    COL_FOOD_ID + " INT(6) NOT NULL, " +
                    COL_DESCRIPTION + " TEXT NOT NULL, " +
                    COL_GRAMS + " DEC(5,2)," +
                    "FOREIGN KEY (" + COL_FOOD_ID + ") REFERENCES " + FoodRepository.TABLE_NAME + "(" + FoodRepository.COL_ID + ")" +
                    ")";

    private static final String SELECT_SQL = "SELECT * FROM " + TABLE_NAME;
    private static final String INSERT_SQL = "INSERT INTO " + TABLE_NAME + "(food_Id, description, grams) VALUES(:foodId, :description, :grams)";
    private static final String UPDATE_SQL = "UPDATE " + TABLE_NAME + " SET food_Id = :foodId, description = :description, grams =:grams WHERE id = :id";

    private NamedParameterJdbcTemplate template = new NamedParameterJdbcTemplate(new JdbcTemplate(DatabaseHelper.getInstance()));

    public PortionRepository() { }

    public int addPortion(Long foodId, Portion portion) {
        SqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", null)
                .addValue("foodId", foodId)
                .addValue("description", portion.getDescription())
                .addValue("grams", portion.getGrams());
        return template.update(INSERT_SQL, params);
    }
    public int updatePortion(Long foodId, Portion portion) {
        SqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", portion.getId())
                .addValue("foodId", foodId)
                .addValue("description", portion.getDescription())
                .addValue("grams", portion.getGrams());
        return template.update(UPDATE_SQL, params);
    }

    public Portion getPortion(Long foodId, Long portionId) {
        SqlParameterSource params = new MapSqlParameterSource()
                .addValue("portionId", portionId)
                .addValue("foodId", foodId);
        String myFoodAlias = SELECT_SQL + " WHERE  " + COL_ID + " = :portionId AND " + COL_FOOD_ID + " = :foodId";
        List<Portion> queryResults = template.query(myFoodAlias, params, new PortionWrapper<Portion>());
        return queryResults.isEmpty() ? null : queryResults.get(0);
    }

    public Portion getPortion(Long foodId, String description) {
        SqlParameterSource params = new MapSqlParameterSource()
                .addValue("description", description)
                .addValue("foodId", foodId);
        String myFoodAlias = SELECT_SQL + " WHERE  " + COL_DESCRIPTION + " = :description AND " + COL_FOOD_ID + " = :foodId";
        List<Portion> queryResults = template.query(myFoodAlias, params, new PortionWrapper<Portion>());
        return queryResults.isEmpty() ? null : queryResults.get(0);
    }

    public List<Portion> getPortions(Long foodId) {
        SqlParameterSource params = new MapSqlParameterSource()
                .addValue("foodId", foodId);
        String myPortions = SELECT_SQL + " WHERE  " + COL_FOOD_ID + " = :foodId";
        return template.query(myPortions, params, new PortionWrapper<Portion>());
    }

    class PortionWrapper<T> implements RowMapper<Portion> {
        @Override
        public Portion mapRow(ResultSet rs, int i) throws SQLException {
            double grams = rs.getDouble(COL_GRAMS);
            boolean gramsWasNull = rs.wasNull();
            return new Portion(rs.getLong(COL_ID),
                    rs.getString(COL_DESCRIPTION),
                    gramsWasNull ? null : grams
            );
        }
    }
}

