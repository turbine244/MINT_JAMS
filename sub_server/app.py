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
    keyword_extractor = yake.KeywordExtractor(lan=lang)
    keywords = keyword_extractor.extract_keywords(text)
    return keywords[:num_keywords]


def normalize_keyword(keyword):
    okt = Okt()
    # 어간 추출로 기본형을 찾기
    normalized = okt.morphs(keyword, stem=True)
    return ' '.join(normalized)  # 기본형 단어들로 변환


def keyword_process(json_data):
    # title 언어 감지 및 키워드 추출
    title_lang = detect_language(json_data['title'])
    title_keyword = extract_keywords(json_data['title'], lang=title_lang, num_keywords=1)

    # description 언어 감지 및 키워드 추출
    description_lang = detect_language(json_data['description'])
    description_keyword = extract_keywords(json_data['description'], lang=description_lang, num_keywords=1)
    
    # comments에서 개별적으로 키워드 추출 후 빈도 계산
    comments_lang = detect_language(' '.join(json_data['comments']))
    keyword_counter = Counter()
    
    for comment in json_data['comments']:
        comment_keywords = extract_keywords(comment, lang=comments_lang, num_keywords=3)
        for keyword, _ in comment_keywords:
            normalized_keyword = normalize_keyword(keyword)  # 기본형으로 변환
            keyword_counter[normalized_keyword] += 1

    # 상위 n개의 키워드와 점수를 할당
    top_comment_keywords = keyword_counter.most_common(100)
    comment_keyword_result = [{'keyword': kw, 'frequency': freq} for kw, freq in top_comment_keywords]

    # 결과를 딕셔너리 형식으로 저장
    keywords = {
        'title_keyword': title_keyword[0][0] if title_keyword else None,
        'description_keyword': description_keyword[0][0] if description_keyword else None,
        'comment_keyword': comment_keyword_result
    }

    return keywords


def analyze_comments_sentiment(comments):
    # 언어 감지
    combined_comments = ' '.join(comments)
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

  
@app.route('/respond', methods=['POST'])                # main server와 연결 함수 이부분에 추가
def respond_request():    
    #데이터 받음
    json_data = request.get_json()
    if not json_data:
       return jsonify({"error": "Invalid JSON format."}), 400

    # title, description, comments에서 키워드 추출
    if 'title' in json_data and 'description' in json_data and 'comments' in json_data:
        keywords = keyword_process(json_data)
        # 결과를 Spring Boot 서버에 전송
        sendToServer(keywords)
        sentiment_scores = analyze_comments_sentiment(json_data['comments'])
        return jsonify(keywords), 200
    else:
        return jsonify({'error': 'Invalid JSON format'}), 400
    
# spring boot 서버로 전송하기    
def sendToServer(keywords):
    server_url = "http://localhost:8081/search"  # Spring Boot 서버 URL
    response = requests.post(server_url, json=keywords)  # JSON 형식으로 전송       

    #try:
    #    response = requests.post(server_url, json=keywords)  # JSON 형식으로 전송
    #    if response.status_code != 200:
    #        print(f"Error sending data to Spring Boot server: {response.status_code}, {response.text}")
    #except requests.exceptions.RequestException as e:
    #    print(f"Failed to send data to the database server: {e}")


# JSON 파일로 저장하는 함수 추가
def save_keywords_to_json(keywords, filename="keywords.json"):
    with open(filename, 'w', encoding='utf-8') as json_file:
        json.dump(keywords, json_file, indent=4, ensure_ascii=False)
    print(f"Keywords have been saved to '{filename}'")

if __name__ == '__main__':
    app.run(debug=True)
