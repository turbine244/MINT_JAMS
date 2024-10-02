from flask import Flask, request, jsonify, render_template
import yake
from collections import Counter
import requests  # DB와 통신하기 위해 사용
import json

app = Flask(__name__)

# YAKE 설정 (한국어, 1~2 단어, 상위 10개 키워드)
kw_extractor = yake.KeywordExtractor(lan="ko", n=2, top=10)

# 키워드 추출 함수
def extract_keywords(text, extractor, top_n=1):
    keywords = extractor.extract_keywords(text)
    return [kw[0] for kw in keywords[:top_n]]  # 상위 n개의 키워드 반환

# DB 서버와 통신해서 데이터 받기                                           (이 부분 확인!!!!!!!!!!!!!!!)
def get_data_from_db():
    db_url = "http://your-db-server-url/get-data"  # DB 서버의 URL을 입력
    try:
        response = requests.get(db_url)  # GET 요청으로 데이터베이스에서 데이터 요청
        if response.status_code == 200:
            return response.json()  # JSON 형식으로 데이터를 반환
        else:
            return None
    except Exception as e:
        print(f"Error connecting to the database: {e}")
        return None

# DB 서버로 전송하기    
def send_data_to_db_server(keywords):
    # DB 서버 URL
    db_server_url = "http://your-db-server-address/api/update_keywords"
    
    # 요청을 보내기 위해 requests 라이브러리 사용
    try:
        response = requests.post(db_server_url, json=keywords)
        return response
    except requests.exceptions.RequestException as e:
        print(f"Failed to send data to the database server: {e}")
        return None
    

def save_keywords_to_json(keywords):
    # 현재 폴더에 'keywords.json' 파일로 저장
    with open('keywords.json', 'w', encoding='utf-8') as json_file:
        json.dump(keywords, json_file, ensure_ascii=False, indent=4)
    print("Keywords have been saved to 'keywords.json'")


# 메인 페이지
@app.route('/')
def index():
    return render_template('index.html')  # HTML 파일 렌더링

# 키워드 추출 API
@app.route('/extract_keywords', methods=['POST'])
def extract_keywords_route():
    # 데이터베이스에서 데이터 요청
    # data = get_data_from_db()

    data = {
                "title": "Gordon Finds A MOUSE! | Kitchen Nightmares | Gordon Ramsay",
                "description": "And they think Gordon planted it...\n\nSeason 5, Episode 1\n\nThe opening episode of a new series sees Gordon in Plainfield, New Jersey, trying to save a floundering soul food restaurant called Blackberry\u0027s. He comes up against the indomitable Shelley, an owner who runs her establishment with an iron fist, and doesn\u0027t want to hear what Gordon has to say. Gordon must find a way to convince her to change her ways if the restaurant is going to stand a chance.\n\nThe home of Gordon Ramsay on YouTube. Recipe tutorials, tips, techniques and the best bits from the archives. From full episodes to compilations, we have new uploads every week - subscribe now to stay up to date!\n\nEnjoyed our video? Make sure to like and comment!\n\nIf you liked this clip check out the rest of Gordon\u0027s channels:\nhttp://www.youtube.com/kitchennightmares\nhttp://www.youtube.com/thefword\nhttp://www.youtube.com/allinthekitchen\n\nMore Gordon Ramsay:\nWebsite: http://www.gordonramsay.com\nFacebook: http://www.facebook.com/GordonRamsay\nTwitter: http://www.twitter.com/GordonRamsay\n\nGordon Finds A MOUSE! | Kitchen Nightmares | Gordon Ramsay\nhttps://www.youtube.com/channel/UCIEv3lZ_tNXHzL3ox-_uUGQ\n\n#GordonRamsay #GordonRamsayRecipes #GordonRamsayCooking #KitchenNightmares",
                "publishedAt": "2024-08-08T16:00:02Z",
                "viewCount": "64417",
                "likeCount": "1503",
                "comments": [
                                "The mouse must have had some chitlins.....",
                                "I would commit unspeakable acts just in the name of protecting mama mary, she is way too pure for this world and deserves so much better than the piece of shit daughter she has what a wonderful human being",
                                "\u003ca href\u003d\"https://www.youtube.com/watch?v\u003d36TX76BvESo\u0026amp;t\u003d837\"\u003e13:57\u003c/a\u003e BROS BARBER KILLED HIS HAIRLINE 💀",
                                "I wonder if she apologised to Gordon and her staff.",
                                "Matten deserves a spot on hells kitchen he said he has been cooking at the culinary school and he ran that kitchen when Shelly abandoned her kitchen. He was a real one!",
                                "Somebody was caught lying in 4K\u003cbr\u003e\u003cbr\u003eAnd it wasn\u0026#39;t Gordon",
                                "What a cry baby!!",
                                "I wanna give Mother Mary a giant hug"
                ]
    }
    
    if not data:
        return jsonify({'error': 'Failed to retrieve data from the database.'}), 500
    
    # title에서 키워드 1개 추출
    title_text = data.get('title', '')
    title_keyword = extract_keywords(title_text, kw_extractor, 1)
    
    # description에서 키워드 1개 추출
    description_text = data.get('description', '')
    description_keyword = extract_keywords(description_text, kw_extractor, 1)
    
    # comments에서 각 댓글마다 키워드 추출 후 가장 빈도 높은 키워드 찾기
    comments = data.get('comments', [])
    comment_keywords = []
    for comment in comments:
        comment_keywords.extend(extract_keywords(comment, kw_extractor, 1))
    
    # 빈도수 높은 키워드 찾기
    most_common_keyword = Counter(comment_keywords).most_common(1)[0][0] if comment_keywords else None
    
    if title_keyword or description_keyword or most_common_keyword:
        keywords = {
            'title_keyword': title_keyword[0] if title_keyword else None,
            'description_keyword': description_keyword[0] if description_keyword else None,
            'most_common_comment_keyword': most_common_keyword
        }

        # JSON 파일로 저장        (임시용)
        save_keywords_to_json(keywords)
        
        # # DB 서버로 JSON 전송
        # response = send_data_to_db_server(keywords)
        # if response.status_code != 200:
        #     return jsonify({'error': 'Failed to send data to the database server.'}), 500
    
    # 결과 반환
    return jsonify(keywords)

if __name__ == '__main__':
    app.run(debug=True)
