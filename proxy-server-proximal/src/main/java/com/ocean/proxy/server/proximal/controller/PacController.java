package com.ocean.proxy.server.proximal.controller;

import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.InputStream;

/**
 * <b>Description:</b>  <br/>
 * <b>@Author:</b> Ocean <br/>
 * <b>@DateTime:</b> 2024/2/4 09:56
 */
@RestController
@RequestMapping("/pac")
public class PacController {

    @GetMapping(value = "/{pacFile}")
    public void getPac(HttpServletResponse response,@PathVariable("pacFile") String pacFile) throws Exception{
        InputStream inputStream = this.getClass().getResourceAsStream("/pac/" + pacFile);
        ServletOutputStream outputStream = response.getOutputStream();
        response.setContentType("application/octet-stream;charset=utf-8");
        response.setHeader("Content-Disposition", String.format("attachment; filename=\"" + pacFile + "\""));
        FileCopyUtils.copy(inputStream, outputStream);
    }
}
