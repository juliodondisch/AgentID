from fastmcp.server import FastMCP
import httpx
import json
import uvicorn
from starlette.applications import Starlette
from starlette.routing import Mount
from src.service import store_query as st


# Logic
FAKE_STORE_API = "https://fakestoreapi.com" #-- Fake store api can be used online with predefined products and data

# MCP Server
mcp = FastMCP("store-mcp-server")

@mcp.tool()
def ping():
    """
    Ping the fake store api
    Returns:
        A string
    """ 
    return st.ping_handler()

@mcp.tool()
def get_tools_list():
    """
    Get the list of tools available in the fake store api
    Returns:
        A list of tool objects (dict)
    """
    return st.get_tools_list_handler()

@mcp.tool()
async def get_products():
    """
    Get all products from the fake store api
    Returns:
        A list of product objects (dict)
    """
    return await st.get_products_handler()

@mcp.tool()
async def get_product(product_id: int):
    """
    Get a specific product by id from the fake store api
    Args:
        product_id: The id of the product to get (int)
    Returns:
        A product object (dict)
    """
    return await st.get_product_handler(product_id)

@mcp.tool()
async def get_categories():
    """
    Get all categories listed from the fake store api
    Returns:
        A list of category objects (dict)
    """
    return await st.get_categories_handler()

@mcp.tool()
async def get_products_by_category(category: str):
    """
    Get all products by specific category from the fake store api
    Args:
        category: The category to get products from (str)
    Returns:
        A list of product objects (dict)
    """
    return await st.get_products_by_category_handler(category)

# @mcp.tool()
# async def get_carts():
#     """
#     Get all carts from the fake store api
#     Returns:
#         A list of cart objects (dict)
#     """
#     return await st.get_carts_handler()

# @mcp.tool()
# async def get_cart(cart_id: int):
#     """
#     Get a cart from the fake store api
#     Args:
#         cart_id: The id of the cart to get (int)
#     Returns:
#         A cart object (dict)
#     """
#     return await st.get_cart_handler(cart_id)

# @mcp.tool()
# async def get_user_cart(user_id: int):
#     """
#     Get a user's cart from the fake store api
#     Args:
#         user_id: The id of the user to get the cart from (int)
#     Returns:
#         A cart object (dict)
#     """
#     return await st.get_user_cart_handler(user_id)

# @mcp.tool()
# async def get_users():
#     """
#     Get all users from the fake store api
#     Returns:
#         A list of user objects (dict)
#     """
#     return await st.get_users_handler()

# @mcp.tool()
# async def get_user(user_id: int):
#     """
#     Get a specific user from the fake store api
#     Args:
#         user_id: The id of the user to get (int)
#     Returns:
#         A user object (dict)
#     """
#     return await st.get_user_handler(user_id)

# @mcp.tool()
# async def login(username: str, password: str):
#     """
#     Login to the fake store api
#     Args:
#         username: The username to login with (str)
#         password: The password to login with (str)
#     Returns:
#         A token (str)
#     """
#     return await st.login_handler(username, password)


# Main
def main():
    #Creates starlette and mounts the mcp
    app = Starlette(
        routes=[
            Mount("/", app=mcp.sse_app()),
        ]
    )
    print(st.ping_handler())
    uvicorn.run(app, host="127.0.0.1", port=5028)

if __name__ == "__main__":
    main()