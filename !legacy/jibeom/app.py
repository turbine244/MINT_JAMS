from flask import Flask, request, jsonify
from googletrans import Translator
import yake
from vaderSentiment.vaderSentiment import SentimentIntensityAnalyzer
import requests  # DB와 통신하기 위해 사용


app = Flask(__name__)

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


def keyword_process(json_data):
    # title 언어 감지 및 키워드 추출
    title_lang = detect_language(json_data['title'])
    title_keyword = extract_keywords(json_data['title'], lang=title_lang, num_keywords=1)

    # description 언어 감지 및 키워드 추출
    description_lang = detect_language(json_data['description'])
    description_keyword = extract_keywords(json_data['description'], lang=description_lang, num_keywords=1)
    
    # comments를 하나의 문자열로 결합 후 언어 감지 및 키워드 추출
    combined_comments = ' '.join(json_data['comments'])
    comments_lang = detect_language(combined_comments)
    comment_keywords = extract_keywords(combined_comments, lang=comments_lang, num_keywords=3)

    # 결과를 딕셔너리 형식으로 저장
    keywords = {
        'title_keyword': title_keyword[0][0] if title_keyword else None,
        'description_keyword': description_keyword[0][0] if description_keyword else None,
        'comment_keyword': [
            {'keyword': comment_keywords[i][0], 'score': 3 - i} for i in range(len(comment_keywords))
        ]
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
    json_data = request.get_json()

    if 'title' in json_data and 'description' in json_data and 'comments' in json_data:
        keywords = keyword_process(json_data)
        sentiment_scores = analyze_comments_sentiment(json_data['comments'])
        return jsonify({'keywords': keywords}), 200
    else:
        return jsonify({'error': 'Invalid JSON format'}), 400
    

if __name__ == '__main__':
    app.run(debug=True)


# curl -X POST http://127.0.0.1:5000/extract_keywords

# data = {
#                 "title": "Gordon Finds A MOUSE! | Kitchen Nightmares | Gordon Ramsay",
#                 "description": "And they think Gordon planted it...\n\nSeason 5, Episode 1\n\nThe opening episode of a new series sees Gordon in Plainfield, New Jersey, trying to save a floundering soul food restaurant called Blackberry\u0027s. He comes up against the indomitable Shelley, an owner who runs her establishment with an iron fist, and doesn\u0027t want to hear what Gordon has to say. Gordon must find a way to convince her to change her ways if the restaurant is going to stand a chance.\n\nThe home of Gordon Ramsay on YouTube. Recipe tutorials, tips, techniques and the best bits from the archives. From full episodes to compilations, we have new uploads every week - subscribe now to stay up to date!\n\nEnjoyed our video? Make sure to like and comment!\n\nIf you liked this clip check out the rest of Gordon\u0027s channels:\nhttp://www.youtube.com/kitchennightmares\nhttp://www.youtube.com/thefword\nhttp://www.youtube.com/allinthekitchen\n\nMore Gordon Ramsay:\nWebsite: http://www.gordonramsay.com\nFacebook: http://www.facebook.com/GordonRamsay\nTwitter: http://www.twitter.com/GordonRamsay\n\nGordon Finds A MOUSE! | Kitchen Nightmares | Gordon Ramsay\nhttps://www.youtube.com/channel/UCIEv3lZ_tNXHzL3ox-_uUGQ\n\n#GordonRamsay #GordonRamsayRecipes #GordonRamsayCooking #KitchenNightmares",
#                 "publishedAt": "2024-08-08T16:00:02Z",
#                 "viewCount": "64417",
#                 "likeCount": "1503",
#                 "comments": [
#                                 "The mouse must have had some chitlins.....",
#                                 "I would commit unspeakable acts just in the name of protecting mama mary, she is way too pure for this world and deserves so much better than the piece of shit daughter she has what a wonderful human being",
#                                 "\u003ca href\u003d\"https://www.youtube.com/watch?v\u003d36TX76BvESo\u0026amp;t\u003d837\"\u003e13:57\u003c/a\u003e BROS BARBER KILLED HIS HAIRLINE 💀",
#                                 "I wonder if she apologised to Gordon and her staff.",
#                                 "Matten deserves a spot on hells kitchen he said he has been cooking at the culinary school and he ran that kitchen when Shelly abandoned her kitchen. He was a real one!",
#                                 "Somebody was caught lying in 4K\u003cbr\u003e\u003cbr\u003eAnd it wasn\u0026#39;t Gordon",
#                                 "What a cry baby!!",
#                                 "I wanna give Mother Mary a giant hug"
#                 ]
# }
