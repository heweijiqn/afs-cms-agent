import com.luciad.imageio.webp.WebPWriteParam;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

public class ImageUtilsDemo {
    public static void main(String[] args) {
        jpg2webp("/home/mi/download/cptest/1.jpeg", "/home/mi/download/cptest/output-1.webp");
        jpg2webp("/home/mi/download/cptest/2.jpg", "/home/mi/download/cptest/output-2.webp");
        jpg2webp("/home/mi/download/cptest/3.png", "/home/mi/download/cptest/output-3.webp");
    }

    public static void jpg2webp(String oldfile, String newfile){
        try {
            // 获取原始文件的编码
            BufferedImage image = ImageIO.read(new File(oldfile));
            // 创建WebP ImageWriter实例
            ImageWriter writer = ImageIO.getImageWritersByMIMEType("image/webp").next();
            // 配置编码参数
            WebPWriteParam writeParam = new WebPWriteParam(writer.getLocale());
            // 设置压缩模式
            writeParam.setCompressionMode(WebPWriteParam.MODE_DEFAULT);
            // 配置ImageWriter输出
            writer.setOutput(new FileImageOutputStream(new File(newfile)));
            // 进行编码，重新生成新图片
            writer.write(null, new IIOImage(image, null, null), writeParam);
            System.out.println("jpg文件转成webp格式成功");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
