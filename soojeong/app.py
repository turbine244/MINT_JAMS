#로컬 연결용으로 수정한 코드

from flask import Flask, request, jsonify, render_template
from flask_sqlalchemy import SQLAlchemy
import yake
from collections import Counter
import requests
import json
from flask_cors import CORS

app = Flask(__name__)
CORS(app)

app.config['SQLALCHEMY_DATABASE_URI'] = 'mysql+pymysql://username:password@localhost:3306/mint_jams_dev'
app.config['SQLALCHEMY_TRACK_MODIFICATIONS'] = False

db = SQLAlchemy(app)

# YAKE 설정 (한국어, 1~2 단어, 상위 10개 키워드)
kw_extractor = yake.KeywordExtractor(lan="ko", n=2, top=10)

def extract_keywords(text, extractor, top_n=1):
    keywords = extractor.extract_keywords(text)
    return [kw[0] for kw in keywords[:top_n]]

# 메인 페이지
@app.route('/')
def index():
    return render_template('index.html')

# Spring Boot 서버로부터 데이터를 받는 엔드포인트
@app.route('/extract_keywords', methods=['POST'])
def your_function():
    data = request.get_json()
    if not data:
        return jsonify({"error": "Invalid JSON format."}), 400

    # title, description, comments에서 키워드 추출
    title_text = data.get('title', '')
    title_keyword = extract_keywords(title_text, kw_extractor, 1)

    description_text = data.get('description', '')
    description_keyword = extract_keywords(description_text, kw_extractor, 1)

    comments = data.get('comments', [])
    comment_keywords = []
    for comment in comments:
        comment_keywords.extend(extract_keywords(comment, kw_extractor, 1))

    most_common_keyword = Counter(comment_keywords).most_common(1)[0][0] if comment_keywords else None
    
    keywords = {
        'title_keyword': title_keyword[0] if title_keyword else None,
        'description_keyword': description_keyword[0] if description_keyword else None,
        'comment_keyword': most_common_keyword
    }

    # 결과를 Spring Boot 서버에 전송
    send_data_to_db_server(keywords)

    return jsonify(keywords)

# spring boot 서버로 전송하기    
def send_data_to_db_server(keywords):
    db_server_url = "http://localhost:8081/youtube/info"  # Spring Boot 서버 URL
    try:
        response = requests.post(db_server_url, json=keywords)  # JSON 형식으로 전송
        if response.status_code != 200:
            print(f"Error sending data to Spring Boot server: {response.status_code}, {response.text}")
    except requests.exceptions.RequestException as e:
        print(f"Failed to send data to the database server: {e}")

if __name__ == '__main__':
    app.run(debug=True)
