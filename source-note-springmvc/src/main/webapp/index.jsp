<%--
  User: haitao
  Date: 2023/1/16
--%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%-- 
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
--%>
<html>
<head>
    <meta http-equiv="Content-Type" content="text/html;charset=UTF-8">
    <title>SpringMVC源码</title>
</head>
<body>
<h1>展示的信息是：${data}</h1>
<button onclick="alertMsg()">触发JavaScript</button>
</body>
</html>
<script>
    function alertMsg() {
        console.log("点击事件")
    }
</script>
