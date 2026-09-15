package kdt.washPrj.vehicle;

import kdt.washPrj.vehicle.dto.VehicleResponseDTO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface VehicleMapper {

    @Select("SELECT * FROM app_api.get_active_vehicle_by_plate(#{plateNumber})")
    VehicleVO findByPlateNumber(@Param("plateNumber") String plateNumber);

    @Select("""
        SELECT app_api.register_my_vehicle(
            #{loginId},
            #{plateNumber},
            #{vehicleType}
        )
    """)
    int registerVehicle(
            @Param("loginId") String loginId,
            @Param("plateNumber") String plateNumber,
            @Param("vehicleType") String vehicleType
    );

    @Select("SELECT * FROM app_api.get_my_vehicle_list(#{loginId})")
    List<VehicleResponseDTO> findMyVehicles(String loginId);

    @Select("SELECT app_api.deactivate_my_vehicle(#{loginId}, #{vehicleId})")
    int deactivateMyVehicle(
            @Param("loginId") String loginId,
            @Param("vehicleId") Integer vehicleId
    );

    @Select("""
    SELECT  COUNT(*)
      FROM  vehicle
     WHERE  customer_id = #{customerId}
       AND  vehicle_status = 'ACTIVE'
    """)
    int countActiveByCustomerOld(
            @Param("customerId") Integer customerId
    );

    @Insert("""
    INSERT
      INTO  vehicle
            (customer_id, plate_number, vehicle_type, created_at, vehicle_status)
    VALUES  (#{customerId},#{plateNumber},#{vehicleType}, #{createdAt}, 'ACTIVE')
        ON  CONFLICT (customer_id, plate_number)
        DO
    UPDATE
       SET  vehicle_status = 'ACTIVE',
            updated_at = CURRENT_TIMESTAMP
    """)
    int registerVehicleOld(VehicleVO vo);

    @Select("""
    SELECT  v.vehicle_id,
            v.plate_number,
            v.vehicle_type,
            v.vehicle_status,
            CASE
                WHEN c.status = 'SUSPENDED' THEN 'SUSPENDED'
                WHEN EXISTS (
                    SELECT 1
                      FROM vehicle_blacklist vb
                     WHERE REPLACE(
                               REPLACE(UPPER(vb.plate_number), ' ', ''),
                               '-',
                               ''
                           ) = REPLACE(
                                   REPLACE(UPPER(v.plate_number), ' ', ''),
                                   '-',
                                   ''
                               )
                       AND vb.active = TRUE
                ) THEN 'RESTRICTED'
                ELSE 'ACTIVE'
            END AS usage_status,
            MAX(wh.wash_start_time) FILTER (
                WHERE wh.wash_status IN (
                    'WASHING',
                    'WASH_DONE',
                    'EXITED'
                )
            ) AS last_wash_at
      FROM vehicle v
      JOIN customer c
        ON c.customer_id = v.customer_id
      LEFT JOIN wash_history wh
        ON wh.vehicle_id = v.vehicle_id
     WHERE v.customer_id = #{customerId}
       AND v.vehicle_status = 'ACTIVE'
     GROUP BY
            v.vehicle_id,
            v.plate_number,
            v.vehicle_type,
            v.vehicle_status,
            c.status
     ORDER BY v.created_at DESC, v.vehicle_id DESC
    """)
    List<VehicleResponseDTO> findByCustomerIdOld(int customerId);

    @Select("""
    SELECT  *
      FROM  vehicle
     WHERE  vehicle_id = #{vehicleId}
       AND  customer_id = #{customerId}
       AND  vehicle_status = 'ACTIVE'
    """)
    VehicleVO findActiveByIdAndCustomerOld(
            @Param("vehicleId") Integer vehicleId,
            @Param("customerId") Integer customerId
    );

    //논리 삭제
    @Update("""
    UPDATE  vehicle
       SET  vehicle_status = 'DEACTIVATED'
     WHERE  vehicle_id = #{vehicleId}
       AND  customer_id = #{customerId}
    """)
    int deactivateByVehicleIdOld(int vehicleId, int customerId);

    @Update("""
    UPDATE vehicle
       SET vehicle_status = 'DEACTIVATED',
           updated_at = CURRENT_TIMESTAMP
     WHERE customer_id = #{customerId}
       AND vehicle_status = 'ACTIVE'
    """)
    int deactivateAll(
            @Param("customerId") Integer customerId
    );
}
