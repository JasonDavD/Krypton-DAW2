package pe.com.krypton.service;

import pe.com.krypton.dto.request.LoginRequest;
import pe.com.krypton.dto.request.RegisterRequest;
import pe.com.krypton.dto.response.AuthResponse;
import pe.com.krypton.dto.response.UsuarioResponse;

public interface AuthService {

    /** Registra un CLIENTE (password hasheado, email único). */
    UsuarioResponse register(RegisterRequest request);

    /** Valida credenciales + que el usuario esté activo y emite el JWT. */
    AuthResponse login(LoginRequest request);
}
