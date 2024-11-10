from flask import Flask, request, jsonify
from googletrans import Translator
import yake
from collections import Counter
from konlpy.tag import Okt
from vaderSentiment.vaderSentiment import SentimentIntensityAnalyzer
import requests  # 서버와 통신하기 위해 사용
from flask_cors import CORS
import json


app = Flask(__name__)
CORS(app)

def detect_language(text):
    translator = Translator()
    return translator.detect(text).lang


def translate_korean_to_english(text):
    translator = Translator()
    translated = translator.translate(text, src='ko', dest='en')
    return translated.text


def extract_keywords(text, lang='en', num_keywords=1):
    keyword_extractor = yake.KeywordExtractor(lan=lang, n=1)
    keywords = keyword_extractor.extract_keywords(text)
    return keywords[:num_keywords]


def normalize_keyword(keyword):
    okt = Okt()
    # 어간 추출로 기본형을 찾기
    normalized = okt.morphs(keyword, stem=True)
    return ' '.join(normalized)  # 기본형 단어들로 변환


def content_keyword_process(json_data):
    combined_content = ' '.join(json_data['content'])  # 리스트를 문자열로 결합
    content_lang = detect_language(combined_content)
    content_keywords = extract_keywords(combined_content, lang=content_lang, num_keywords=3)

    keyword_result = []
    for keyword, _ in content_keywords:
        if content_lang == 'ko':
            keyword = normalize_keyword(keyword)  # 한국어 기본형 변환
        keyword_result.append({"keyword": keyword, "found": 1})  # 'found' 값을 1로 설정

    keywords = {
        'keywords': keyword_result
    }

    return keywords


def comment_keyword_process(json_data):
    combined_comment = ' '.join(json_data['comment'])  # 리스트를 문자열로 결합
    comment_lang = detect_language(combined_comment)
    keyword_counter = Counter()
    
    for comment in json_data['comment']:
        comment_keywords = extract_keywords(comment, lang=comment_lang, num_keywords=1)
        for keyword, _ in comment_keywords:
            if comment_lang == 'ko':
                keyword = normalize_keyword(keyword)  # 기본형으로 변환
            keyword_counter[keyword] += 1

    #키워드와 점수를 할당
    comment_keyword_result = [{'keyword': kw, 'found': fnd} for kw, fnd in keyword_counter.items()]

    # 결과를 딕셔너리 형식으로 저장
    keywords = {
        'keywords': comment_keyword_result
    }

    return keywords


def analyze_comments_sentiment(json_data):
    # 언어 감지
    combined_comments = ' '.join(json_data['comment'])
    lang = detect_language(combined_comments)
    
    # VADER 분석기 초기화
    analyzer = SentimentIntensityAnalyzer()
    
    if lang == 'ko':  # 한국어인 경우
        translated_comments = translate_korean_to_english(combined_comments)
        # 감정 분석
        sentiment_scores = analyzer.polarity_scores(translated_comments)
    else:  # 영어인 경우
        # 감정 분석
        sentiment_scores = analyzer.polarity_scores(combined_comments)
    
    return sentiment_scores


@app.route('/respondK', methods=['POST'])                # main server와 연결 함수 이부분에 추가
def respondK_request():    
    #데이터 받음
    json_data = request.get_json()
    if not json_data:
       return jsonify({"error": "Invalid JSON format."}), 400

    # data에서 키워드 추출
    if 'content' in json_data:
        keywords = content_keyword_process(json_data)
        # 결과를 Spring Boot 서버에 전송
        sendToServer(keywords)
        return jsonify(keywords), 200
    elif 'comment' in json_data:
        keywords = comment_keyword_process(json_data)
        # 결과를 Spring Boot 서버에 전송
        sendToServer(keywords)
        return jsonify(keywords), 200
    else:
        return jsonify({'error': 'Invalid JSON format'}), 400
    

@app.route('/respondS', methods=['POST'])                # main server와 연결 함수 이부분에 추가
def respondS_request():    
    #데이터 받음
    json_data = request.get_json()
    if not json_data:
       return jsonify({"error": "Invalid JSON format."}), 400

    # data에서 키워드 추출
    if 'data' in json_data:
        sentiment_scores = analyze_comments_sentiment(json_data)
        # 결과를 Spring Boot 서버에 전송
        sendToServer(sentiment_scores)
        return jsonify(sentiment_scores), 200
    else:
        return jsonify({'error': 'Invalid JSON format'}), 400
    

# spring boot 서버로 전송하기    
def sendToServer(json_data):
    server_url = "http://localhost:8081/search"  # Spring Boot 서버 URL
    response = requests.post(server_url, json=json_data)  # JSON 형식으로 전송       

    # try:
    #    response = requests.post(server_url, json=keywords)  # JSON 형식으로 전송
    #    if response.status_code != 200:
    #        print(f"Error sending data to Spring Boot server: {response.status_code}, {response.text}")
    # except requests.exceptions.RequestException as e:
    #    print(f"Failed to send data to the database server: {e}")


# JSON 파일로 저장하는 함수 추가
def save_keywords_to_json(keywords, filename="keywords.json"):
    with open(filename, 'w', encoding='utf-8') as json_file:
        json.dump(keywords, json_file, indent=4, ensure_ascii=False)
    print(f"Keywords have been saved to '{filename}'")

if __name__ == '__main__':
    app.run(debug=True)
