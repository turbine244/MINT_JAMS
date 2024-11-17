from flask import Flask, request, jsonify
from googletrans import Translator
import yake
from collections import Counter
from konlpy.tag import Okt
from vaderSentiment.vaderSentiment import SentimentIntensityAnalyzer
import requests  # 서버와 통신하기 위해 사용
from flask_cors import CORS
import json
import re


app = Flask(__name__)
CORS(app)


def detect_language(text):
    if not text or not isinstance(text, str) or text.strip() == "":
        return "unknown"

    translator = Translator()
    try:
        detected_lang = translator.detect(text).lang
        return detected_lang
    except Exception as e:
        print(f"Error detecting language: {e}")
        return "unknown"



def translate_korean_to_english(text):
    translator = Translator()
    translated = translator.translate(text, src='ko', dest='en')
    return translated.text


def get_keywords(text, num_keywords=1):
    keyword_extractor = yake.KeywordExtractor(n=1, top=num_keywords)
    keywords = keyword_extractor.extract_keywords(text)
    return keywords


def clean_text(text):
    text = re.sub(r'[^\w\s\uAC00-\uD7A3\.\'\"\!\?]', '', text)
    text = re.sub(r'([ㄱ-ㅎㅏ-ㅣ])\1+', '', text)
    return text


def normalize_keyword(text):
    okt = Okt()
    words = okt.pos(text, norm=True, stem=True)  # 품사 태깅 (형태소 분석)
    cleaned_text = "".join(word for word, pos in words if pos != "Josa")  # 조사(Josa)인 부분만 제거
    return cleaned_text


def remove_english_stopwords(keyword):
    # 영어의 불필요한 단어 목록 (연결부사와 관사)
    stopwords = {"the", "and", "or", "but", "however", "therefore", "moreover", "furthermore", 
                 "thus", "meanwhile", "instead", "nevertheless", "otherwise", "also", "yet", "so", 
                 "a", "an", "this", "that", "he", "him", "she", "her", "they", "them", "is", "am",
                "are", "was", "were", "in", "on", "at", "by", "for"}
    # 키워드가 불필요한 단어라면 빈 문자열 반환
    return "" if keyword.lower() in stopwords else keyword


def content_keyword_process(json_data):
    combined_content = ' '.join(json_data['content'])  # 리스트를 문자열로 결합
    content_keywords = get_keywords(combined_content, num_keywords=10)

    keyword_result = []
    for keyword, _ in content_keywords:
        keyword = clean_text(keyword)
        if not keyword:  
            continue

        if detect_language(keyword) == 'ko':
            keyword = normalize_keyword(keyword)  # 한국어 기본형 변환
        elif detect_language(keyword) == 'en':
            keyword = remove_english_stopwords(keyword)  # 영어 불필요한 단어 제거
            if not keyword:  # 불필요한 단어 제거 후 키워드가 비었으면 넘어감
                continue
            # 영어 키워드 첫 글자만 대문자로, 나머지 소문자로 변경
            keyword = keyword[0].upper() + keyword[1:].lower()

        keyword_result.append({"keyword": keyword, "found": 1})  # 'found' 값을 1로 설정

        if len(keyword_result) == 3:  # 키워드가 3개가 되면 결과 반환
            return {'keywords': keyword_result}

    keywords = {
        'keywords': keyword_result
    }

    return keywords


def comment_keyword_process(json_data):
    keyword_counter = Counter()
    
    for comment in json_data['comment']:
        comment_keywords = get_keywords(comment, num_keywords=5)
        for keyword, _ in comment_keywords:
            keyword = clean_text(keyword)
            if not keyword:
                continue

            if detect_language(keyword) == 'ko':
                keyword = normalize_keyword(keyword)  # 기본형으로 변환
            elif detect_language(keyword) == 'en':
                keyword = remove_english_stopwords(keyword)  # 영어 불필요한 단어 제거
                if not keyword:
                    continue
                # 영어 키워드 첫 글자만 대문자로, 나머지 소문자로 변환
                keyword = keyword[0].upper() + keyword[1:].lower()

            keyword_counter[keyword] += 1
            break

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
