package kdt.washPrj.vehicle;

import jakarta.validation.Valid;
import kdt.washPrj.vehicle.dto.VehicleRequestDTO;
import kdt.washPrj.vehicle.dto.VehicleResponseDTO;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;

import java.util.List;

@RestController
@RequestMapping("api/customer/vehicles")
public class VehicleController {
    private final VehicleService service;

    public VehicleController(VehicleService service) {
        this.service = service;
    }


    @PostMapping
    public int vehicleRegister(
            Authentication authentication,
            @Valid @RequestBody VehicleRequestDTO dto
    ){
        return service.registerVehicle(dto, authentication.getName());
    }

    @GetMapping
    public List<VehicleResponseDTO> vehicleByCustomer(Authentication authentication){
        return service.vehicleByCustomer(authentication.getName());
    }

    @DeleteMapping("{vehicleId}")
    public int vehicleDelete(
            Authentication authentication,
            @PathVariable("vehicleId") Integer vehicleId
    ){
        return service.vehicleDeactivate(vehicleId, authentication.getName());
    }

}
