package kdt.washPrj.device;

import kdt.washPrj.device.dto.DeviceCreateDTO;
import kdt.washPrj.device.dto.DeviceResponseDTO;
import kdt.washPrj.device.dto.DeviceUpdateDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DeviceMapper {

    @Select("SELECT * FROM app_api.get_my_device_list(#{loginId})")
    List<DeviceResponseDTO> findAll(String loginId);

    @Select("SELECT * FROM app_api.get_my_device_detail(#{loginId}, #{deviceId})")
    DeviceResponseDTO findOne(
            @Param("loginId") String loginId,
            @Param("deviceId") Long deviceId
    );

    @Select("""
        SELECT app_api.update_my_device(
            #{loginId},
            #{deviceId},
            #{dto.deviceName},
            #{dto.status}
        )
    """)
    int update(
            @Param("loginId") String loginId,
            @Param("deviceId") Long deviceId,
            @Param("dto") DeviceUpdateDTO dto
    );

    @Select("""
        SELECT app_api.create_my_device(
            #{loginId},
            #{dto.washSiteId},
            #{dto.deviceName},
            #{dto.deviceType},
            #{dto.status}
        )
    """)
    int insert(
            @Param("loginId") String loginId,
            @Param("dto") DeviceCreateDTO dto
    );

    @Select("SELECT app_api.remove_my_device(#{loginId}, #{deviceId})")
    int remove(
            @Param("loginId") String loginId,
            @Param("deviceId") Long deviceId
    );
}
