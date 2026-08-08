from hanspell import spell_checker

result = spell_checker.check("아버지가방에들어가신다.")

if result.result:
    print("원문:", result.original)
    print("교정문:", result.checked)
    print("오류 수:", result.errors)
    print("단어별 결과:", result.words)
    print("처리 시간:", result.time)
else:
    print("맞춤법 검사에 실패했습니다.")