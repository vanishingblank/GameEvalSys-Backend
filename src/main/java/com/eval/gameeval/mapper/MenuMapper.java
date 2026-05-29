package com.eval.gameeval.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eval.gameeval.models.entity.Menu;
import com.eval.gameeval.models.entity.RoleMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
@Repository
public interface MenuMapper extends BaseMapper<Menu> {

    @Select("""
      SELECT id, parent_id AS parentId, menu_code AS menuCode, menu_type AS menuType,
       title, path, route_name AS routeName, icon, hidden,
       component_code AS componentCode, sort_num AS sortNum,
       is_enabled AS isEnabled, is_deleted AS isDeleted,
       create_time AS createTime, update_time AS updateTime
      FROM sys_menu
      WHERE is_deleted = 0
      ORDER BY sort_num ASC, id ASC
      """)
    List<Menu> selectAllMenus();

    @Select("""
      SELECT id, parent_id AS parentId, menu_code AS menuCode, menu_type AS menuType,
       title, path, route_name AS routeName, icon, hidden,
       component_code AS componentCode, sort_num AS sortNum,
       is_enabled AS isEnabled, is_deleted AS isDeleted,
       create_time AS createTime, update_time AS updateTime
      FROM sys_menu
      WHERE id = #{id} AND is_deleted = 0
      LIMIT 1
      """)
    Menu selectById(@Param("id") Long id);

    @Select("""
      SELECT id, parent_id AS parentId, menu_code AS menuCode, menu_type AS menuType,
       title, path, route_name AS routeName, icon, hidden,
       component_code AS componentCode, sort_num AS sortNum,
       is_enabled AS isEnabled, is_deleted AS isDeleted,
       create_time AS createTime, update_time AS updateTime
      FROM sys_menu
      WHERE menu_code = #{menuCode} AND is_deleted = 0
      LIMIT 1
      """)
    Menu selectByMenuCode(@Param("menuCode") String menuCode);

    @Select("""
      SELECT id, parent_id AS parentId, menu_code AS menuCode, menu_type AS menuType,
       title, path, route_name AS routeName, icon, hidden,
       component_code AS componentCode, sort_num AS sortNum,
       is_enabled AS isEnabled, is_deleted AS isDeleted,
       create_time AS createTime, update_time AS updateTime
      FROM sys_menu
      WHERE route_name = #{routeName} AND is_deleted = 0
      LIMIT 1
      """)
    Menu selectByRouteName(@Param("routeName") String routeName);

    @Select("SELECT DISTINCT role_code FROM sys_role_menu WHERE menu_code = #{menuCode} ORDER BY role_code")
    List<String> selectRoleCodesByMenuCode(@Param("menuCode") String menuCode);

    @Select("""
            SELECT id, role_code AS roleCode, menu_code AS menuCode, create_time AS createTime
            FROM sys_role_menu
            ORDER BY menu_code ASC, role_code ASC, id ASC
            """)
    List<RoleMenu> selectAllRoleMenus();

    @Select("""
            SELECT DISTINCT
                m.id,
                m.parent_id AS parentId,
                m.menu_code AS menuCode,
                m.menu_type AS menuType,
                m.title,
                m.path,
                m.route_name AS routeName,
                m.icon,
                m.hidden,
                m.component_code AS componentCode,
                m.sort_num AS sortNum,
                m.is_enabled AS isEnabled,
                m.is_deleted AS isDeleted,
                m.create_time AS createTime,
                m.update_time AS updateTime
            FROM sys_menu m
            INNER JOIN sys_role_menu rm ON m.menu_code = rm.menu_code
            WHERE rm.role_code = #{roleCode}
              AND m.is_enabled = 1
              AND m.is_deleted = 0
              AND m.menu_type <> 'button'
            ORDER BY m.sort_num ASC, m.id ASC
            """)
    List<Menu> selectAccessibleMenusByRoleCode(@Param("roleCode") String roleCode);

        @Insert("""
            INSERT INTO sys_menu(
              parent_id, menu_code, menu_type, title, path, route_name,
              icon, hidden, component_code, sort_num, is_enabled, is_deleted,
              create_time, update_time
            ) VALUES (
              #{parentId}, #{menuCode}, #{menuType}, #{title}, #{path}, #{routeName},
              #{icon}, #{hidden}, #{componentCode}, #{sortNum}, #{isEnabled}, #{isDeleted},
              #{createTime}, #{updateTime}
            )
            """)
        int insertMenu(Menu menu);

        @Update("""
            UPDATE sys_menu
            SET parent_id = #{parentId},
              menu_type = #{menuType},
              title = #{title},
              path = #{path},
              route_name = #{routeName},
              icon = #{icon},
              hidden = #{hidden},
              component_code = #{componentCode},
              sort_num = #{sortNum},
              is_enabled = #{isEnabled},
              update_time = #{updateTime}
            WHERE id = #{id} AND is_deleted = 0
            """)
        int updateMenu(Menu menu);

        @Update("UPDATE sys_menu SET is_deleted = 1, is_enabled = 0, update_time = #{updateTime} WHERE id = #{id} AND is_deleted = 0")
        int softDeleteMenuById(@Param("id") Long id, @Param("updateTime") LocalDateTime updateTime);

        @Update("""
            <script>
            UPDATE sys_menu
            SET is_deleted = 1, is_enabled = 0, update_time = #{updateTime}
            WHERE id IN
            <foreach collection='ids' item='id' open='(' separator=',' close=')'>
              #{id}
            </foreach>
            AND is_deleted = 0
            </script>
            """)
        int softDeleteMenuByIds(@Param("ids") List<Long> ids, @Param("updateTime") LocalDateTime updateTime);

        @Delete("""
            <script>
            DELETE FROM sys_role_menu
            WHERE menu_code IN
            <foreach collection='menuCodes' item='menuCode' open='(' separator=',' close=')'>
              #{menuCode}
            </foreach>
            </script>
            """)
        int deleteRoleMenusByMenuCodes(@Param("menuCodes") List<String> menuCodes);

        @Delete("DELETE FROM sys_role_menu WHERE menu_code = #{menuCode}")
        int deleteRoleMenusByMenuCode(@Param("menuCode") String menuCode);

        @Insert("""
            <script>
            INSERT INTO sys_role_menu(role_code, menu_code, create_time)
            VALUES
            <foreach collection='roleCodes' item='roleCode' separator=','>
              (#{roleCode}, #{menuCode}, NOW())
            </foreach>
            </script>
            """)
        int batchInsertRoleMenus(@Param("menuCode") String menuCode, @Param("roleCodes") List<String> roleCodes);
}
