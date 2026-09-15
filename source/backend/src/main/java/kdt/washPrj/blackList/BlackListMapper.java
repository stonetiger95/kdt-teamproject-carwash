package kdt.washPrj.blackList;

import kdt.washPrj.blackList.dto.AdminVehicleDetailDTO;
import kdt.washPrj.blackList.dto.AdminVehicleListDTO;
import kdt.washPrj.blackList.dto.AdminVehicleSearchDTO;
import kdt.washPrj.blackList.dto.VehicleBlackListDTO;
import kdt.washPrj.blackList.dto.VehicleBlackListSearchDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface BlackListMapper {

    @Select("""
        SELECT *
        FROM app_api.get_admin_vehicle_page_filtered(
            #{loginId},
            #{search.keyword},
            #{search.customerType},
            #{search.blacklistStatus},
            #{search.size},
            #{search.offset}
        )
    """)
    List<AdminVehicleListDTO> getVehicleList(
            @Param("loginId") String loginId,
            @Param("search") AdminVehicleSearchDTO searchDTO
    );

    @Select("SELECT app_api.add_manual_blacklist(#{loginId}, #{plateNumber}, #{reason})")
    int addManualBlackList(
            @Param("loginId") String loginId,
            @Param("plateNumber") String plateNumber,
            @Param("reason") String reason
    );

    @Select("SELECT * FROM app_api.get_admin_vehicle_detail_bundle(#{loginId}, #{plateNumber})")
    AdminVehicleDetailDTO getVehicleDetail(
            @Param("loginId") String loginId,
            @Param("plateNumber") String plateNumber
    );

    @Select("SELECT * FROM app_api.get_active_blacklist_by_plate(#{plateNumber})")
    VehicleBlackListVO findBlacklistsByPlateNumber(String plateNumber);

    @Select("""
        SELECT *
        FROM app_api.get_admin_blacklist_page(
            #{loginId},
            #{search.keyword},
            #{search.blacklistType},
            #{search.active},
            #{search.size},
            #{search.offset}
        )
    """)
    List<VehicleBlackListDTO> getBlackList(
            @Param("loginId") String loginId,
            @Param("search") VehicleBlackListSearchDTO searchDTO
    );

    @Select("SELECT app_api.release_manual_blacklist(#{loginId}, #{plateNumber})")
    int releaseManualBlackList(
            @Param("loginId") String loginId,
            @Param("plateNumber") String plateNumber
    );

    @Select("SELECT app_api.suspend_customer_vehicles(#{loginId}, #{customerId})")
    int insertSuspendedCustomerVehicles(
            @Param("loginId") String loginId,
            @Param("customerId") Integer customerId
    );

    @Select("SELECT app_api.release_suspended_customer_vehicles(#{loginId}, #{customerId})")
    int releaseSuspendedCustomerVehicles(
            @Param("loginId") String loginId,
            @Param("customerId") Integer customerId
    );

    @Update("""
        UPDATE vehicle_blacklist
           SET active = FALSE,
               released_at = CURRENT_TIMESTAMP
         WHERE customer_id = #{customerId}
           AND REPLACE(
                   REPLACE(UPPER(plate_number), ' ', ''),
                   '-',
                   ''
               ) = #{plateNumber}
           AND blacklist_type = 'CUSTOMER_SUSPENDED'
           AND active = TRUE
    """)
    int releaseSuspendedCustomerVehicle(
            @Param("customerId") Integer customerId,
            @Param("plateNumber") String plateNumber
    );

    @Select("""
    WITH plate_universe AS (
        SELECT  plate_key,
                MAX(plate_number) AS plate_number
          FROM  (
                SELECT  REPLACE(
                            REPLACE(UPPER(plate_number), ' ', ''),
                            '-',
                            ''
                        ) AS plate_key,
                        plate_number
                  FROM  vehicle
                 UNION ALL
                SELECT  REPLACE(
                            REPLACE(UPPER(plate_number), ' ', ''),
                            '-',
                            ''
                        ) AS plate_key,
                        plate_number
                  FROM  entry_exit
                ) known_plate
         GROUP
            BY  plate_key
    ), latest_vehicle AS (
        SELECT  DISTINCT ON (plate_key)
                plate_key,
                plate_number,
                customer_id,
                vehicle_status
          FROM  (
                SELECT  REPLACE(
                            REPLACE(UPPER(plate_number), ' ', ''),
                            '-',
                            ''
                        ) AS plate_key,
                        plate_number,
                        customer_id,
                        vehicle_status,
                        updated_at,
                        vehicle_id
                  FROM  vehicle
                ) registered_vehicle
         ORDER
            BY  plate_key,
                CASE WHEN vehicle_status = 'ACTIVE' THEN 0 ELSE 1 END,
                updated_at DESC NULLS LAST,
                vehicle_id DESC
    ), visit_stats AS (
        SELECT  REPLACE(
                    REPLACE(UPPER(plate_number), ' ', ''),
                    '-',
                    ''
                ) AS plate_key,
                COUNT(*) AS visit_count,
                MAX(entry_time) AS last_entry_time
          FROM  entry_exit
         GROUP
            BY  REPLACE(
                    REPLACE(UPPER(plate_number), ' ', ''),
                    '-',
                    ''
                )
    )
    SELECT  COALESCE(v.plate_number, pu.plate_number) AS plate_number,
            v.customer_id,
            c.name AS customer_name,
            CASE
                WHEN v.customer_id IS NULL THEN 'NON_MEMBER'
                ELSE 'MEMBER'
            END AS customer_type,
            c.status AS customer_status,
            v.vehicle_status,
            COALESCE(vs.visit_count, 0) AS visit_count,
            vs.last_entry_time,
            (
            EXISTS (
                SELECT  1
                  FROM  vehicle_blacklist vb
                 WHERE  REPLACE(
                            REPLACE(UPPER(vb.plate_number), ' ', ''),
                            '-',
                            ''
                        ) = pu.plate_key
                   AND  vb.active = TRUE
            )
            OR COALESCE(c.status = 'SUSPENDED', FALSE)
            ) AS blacklisted
      FROM  plate_universe pu
      LEFT
      JOIN  latest_vehicle v
        ON  v.plate_key = pu.plate_key
      LEFT
      JOIN  customer c
        ON  c.customer_id = v.customer_id
      LEFT
      JOIN  visit_stats vs
        ON  vs.plate_key = pu.plate_key
     WHERE  (
             #{keyword} = ''
             OR pu.plate_key LIKE CONCAT(
                    '%',
                    REPLACE(
                        REPLACE(UPPER(#{keyword}), ' ', ''),
                        '-',
                        ''
                    ),
                    '%'
                )
            )
     ORDER
        BY  last_entry_time DESC NULLS LAST,
            plate_number
     LIMIT  #{size}
    OFFSET  #{offset}
    """)
    List<AdminVehicleListDTO> getVehicleListOld(
            AdminVehicleSearchDTO searchDTO
    );

    @Select("""
    WITH plate_universe AS (
        SELECT  DISTINCT
                REPLACE(
                    REPLACE(UPPER(plate_number), ' ', ''),
                    '-',
                    ''
                ) AS plate_key
          FROM  (
                SELECT plate_number FROM vehicle
                 UNION ALL
                SELECT plate_number FROM entry_exit
                ) known_plate
    )
    SELECT  COUNT(*)
      FROM  plate_universe
     WHERE  (
             #{keyword} = ''
             OR plate_key LIKE CONCAT(
                    '%',
                    REPLACE(
                        REPLACE(UPPER(#{keyword}), ' ', ''),
                        '-',
                        ''
                    ),
                    '%'
                )
            )
    """)
    Integer countUniqueVehicle(
            AdminVehicleSearchDTO searchDTO
    );

}
