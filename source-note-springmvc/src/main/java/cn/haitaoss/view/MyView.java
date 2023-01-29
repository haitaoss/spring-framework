package cn.haitaoss.view;

import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.view.AbstractTemplateView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-01-27 21:04
 */
public class MyView extends AbstractTemplateView {
    @Override
    protected void renderMergedTemplateModel(Map<String, Object> model, HttpServletRequest request,
                                             HttpServletResponse response) throws Exception {
        // 拿到资源文件路径
        String url = getUrl();
        // 读文件
        Resource resource = getApplicationContext().getResource(url);
        // 将文件写到输出流
        IOUtils.copy(resource.getInputStream(), response.getOutputStream());
        // 设置编码
        response.setCharacterEncoding("UTF-8");
        // 相应的内容是文本
        response.setContentType(MediaType.TEXT_HTML_VALUE);
    }
}
