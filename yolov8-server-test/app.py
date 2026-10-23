from flask import Flask, render_template, Response, request, jsonify
import cv2
import numpy as np
from ultralytics import YOLO
import base64
from io import BytesIO
from PIL import Image

app = Flask(__name__)

# 加载YOLOv8模型（首次运行会自动下载）
model = YOLO('yolov8n.pt')  # 使用nano版本模型，平衡速度和精度


# 视频流生成函数
def generate_frames(rtsp_url):
    # 打开RTSP流
    cap = cv2.VideoCapture(rtsp_url)

    # 检查流是否成功打开
    if not cap.isOpened():
        raise RuntimeError("无法打开RTSP流，请检查URL是否正确")

    while True:
        # 读取一帧
        success, frame = cap.read()
        if not success:
            break

        # 转换为JPEG格式
        ret, buffer = cv2.imencode('.jpg', frame)
        frame = buffer.tobytes()

        # 使用multipart/x-mixed-replace格式推送
        yield (b'--frame\r\n'
               b'Content-Type: image/jpeg\r\n\r\n' + frame + b'\r\n')

    # 释放资源
    cap.release()


# 捕获单帧并进行YOLOv8识别
def process_single_frame(rtsp_url):
    # 打开RTSP流
    cap = cv2.VideoCapture(rtsp_url)

    # 检查流是否成功打开
    if not cap.isOpened():
        return None, "无法打开RTSP流，请检查URL是否正确"

    # 读取一帧
    success, frame = cap.read()
    cap.release()  # 立即释放，不需要保持连接

    if not success:
        return None, "无法从RTSP流中读取帧"

    # 使用YOLOv8进行检测
    results = model(frame)

    # 处理检测结果：绘制边界框和标签
    detected_frame = results[0].plot()

    # 提取检测信息
    detections = []
    for box in results[0].boxes:
        cls = int(box.cls[0])
        conf = float(box.conf[0])
        x1, y1, x2, y2 = map(int, box.xyxy[0])
        detections.append({
            "class": model.names[cls],
            "confidence": f"{conf:.2f}",
            "bbox": [x1, y1, x2, y2]
        })

    # 将OpenCV图像转换为base64格式以便在网页中显示
    img = cv2.cvtColor(detected_frame, cv2.COLOR_BGR2RGB)
    pil_img = Image.fromarray(img)
    buffer = BytesIO()
    pil_img.save(buffer, format="JPEG")
    img_str = base64.b64encode(buffer.getvalue()).decode('utf-8')

    return img_str, detections


# 主页路由
@app.route('/')
def index():
    return render_template('index.html')


# 视频流页面路由
@app.route('/stream')
def stream_page():
    rtsp_url = request.args.get('url', '')
    return render_template('stream.html', rtsp_url=rtsp_url)


# 视频流源路由
@app.route('/video_feed')
def video_feed():
    rtsp_url = request.args.get('url', '')
    if not rtsp_url.startswith('rtsp://'):
        return "无效的RTSP URL，请使用rtsp://开头的URL", 400

    return Response(generate_frames(rtsp_url),
                    mimetype='multipart/x-mixed-replace; boundary=frame')


# 目标检测页面路由
@app.route('/detect')
def detect_page():
    rtsp_url = request.args.get('url', '')
    return render_template('detect.html', rtsp_url=rtsp_url)


# 处理目标检测请求的路由
@app.route('/process_detection')
def process_detection():
    rtsp_url = request.args.get('url', '')
    if not rtsp_url.startswith('rtsp://'):
        return jsonify({"error": "无效的RTSP URL，请使用rtsp://开头的URL"}), 400

    img_str, detections = process_single_frame(rtsp_url)

    if img_str is None:
        return jsonify({"error": detections}), 500

    return jsonify({
        "image": img_str,
        "detections": detections
    })


if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=True)
